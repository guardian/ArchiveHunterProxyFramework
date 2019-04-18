import java.net.URLDecoder

import com.amazonaws.services.ecs.model.AmazonECSException
import com.amazonaws.services.ecs.{AmazonECS, AmazonECSClientBuilder}
import com.amazonaws.services.elastictranscoder.{AmazonElasticTranscoder, AmazonElasticTranscoderClientBuilder}
import com.amazonaws.services.elastictranscoder.model._
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.SNSEvent
import com.amazonaws.services.sns.AmazonSNSClientBuilder
import com.amazonaws.services.sns.model.PublishRequest
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClientBuilder}
import com.amazonaws.services.sqs.model.SendMessageRequest

import scala.collection.JavaConverters._
import io.circe.syntax._
import io.circe.generic.auto._

import scala.annotation.switch
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._

class RequestLambdaMain extends RequestHandler[SNSEvent,Unit] with RequestModelEncoder with JobReportStatusEncoder {
  def getSnsClient = AmazonSNSClientBuilder.defaultClient()
  def getSqsClient = AmazonSQSClientBuilder.defaultClient()
  def getEcsClient = AmazonECSClientBuilder.defaultClient()
  def getEtsClient = AmazonElasticTranscoderClientBuilder.defaultClient()

  implicit val ecsClient:AmazonECS = getEcsClient
  protected val etsPipelineManager = new ETSPipelineManager
  protected val snsClient = getSnsClient

  protected def randomStringFromCharList(length: Int, chars: Seq[Char]): String = {
    val sb = new StringBuilder
    for (i <- 1 to length) {
      val randomNum = util.Random.nextInt(chars.length)
      sb.append(chars(randomNum))
    }
    sb.toString
  }

  /**
    * Generate a random alphanumeric string
    * @param length number of characters in the string
    * @return the random string
    */
  protected def randomAlphaNumericString(length: Int): String = {
    val chars = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
    randomStringFromCharList(length, chars)
  }

  /**
    * should the given ETS error be sent to the flood queue?
    * currently, we only push to the flood queue if we receive a ThrottlingException.
    * @param err AmazonElasticTranscoderException that occurred
    * @return a boolean indicating whether to go to flood queue or not.
    */
  def checkETSShouldFloodqueue(err:Throwable) = {
    try {
      val etsError = err.asInstanceOf[AmazonElasticTranscoderException]
      etsError.getErrorCode=="ThrottlingException"
    } catch {
      case err:ClassCastException=>
        false
      case err:Throwable=>
        println(err.toString)
        false
    }
  }


  def doSetupPipeline(model:RequestModel, fromBucket:String, toBucket:String, settings:Settings)(implicit etsClient:AmazonElasticTranscoder) = {
    val pipelineName = s"archivehunter_${randomAlphaNumericString(10)}"
    println(s"Attempting to create a pipeline request with name $pipelineName")
    val createRq = new CreatePipelineRequest()
      .withInputBucket(fromBucket)
      .withName(pipelineName)
      .withNotifications(new Notifications().withCompleted(settings.etsMessageTopic).withError(settings.etsMessageTopic).withWarning(settings.etsMessageTopic).withProgressing(settings.etsMessageTopic))
      .withOutputBucket(toBucket)
      .withRole(settings.etsRoleArn)
    etsPipelineManager.createEtsPipeline(createRq,settings.etsRoleArn)
      .flatMap(pipeline=>etsPipelineManager.waitForCompletion(pipeline.getId)) match {
      case Success(pipelineId)=>
        println(s"Successfully created pipeline: $pipelineId")
        val replyMsg = MainAppReply.withPlainLog(JobReportStatus.SUCCESS,Some(pipelineId),model.jobId,"",None,None,None)
        snsClient.publish(new PublishRequest().withMessage(replyMsg.asJson.toString()).withTopicArn(settings.replyTopic))
        Right("Created pipeline")
      case Failure(err)=>
        println(s"Could not create pipeline: $err")
        val replyMsg = MainAppReply.withPlainLog(JobReportStatus.FAILURE,None,model.jobId,"",Some(err.toString),None,None)
        snsClient.publish(new PublishRequest().withMessage(replyMsg.asJson.toString()).withTopicArn(settings.replyTopic))
        Left("Could not create pipeline")
    }
  }

  /**
    * re-serialize the request and push it onto the flood queue.
    * @param sqsClient AmazonSQS instance
    * @param rq requestModel to send
    * @param floodQueue URL of the flood queue
    * @return a Try, with either the SendMessageResult or an error
    */
  protected def sendToFloodQueue(sqsClient:AmazonSQS, rq:RequestModel, floodQueue:String) = Try {
    println("Sending message to flood queue")
    val sendRq = new SendMessageRequest()
      .withQueueUrl(floodQueue)
      .withMessageBody(rq.asJson.toString)
      .withDelaySeconds(randomDelay) //use a randomised delay so the messages don't all come back through at the same time

    sqsClient.sendMessage(sendRq)
  }

  /**
    * check whether there are more tasks in an active state than we should have.
    * if the request fails, then false is returned; the assumption is that any failure in this would also
    * cause further requests to fail.
    * @param taskMgr ContainerTaskManager instance
    * @param settings Settings object
    * @return boolean, True if we should run the task immediately and False if we should push to flood queue
    */
  protected def checkTaskCount(taskMgr:ContainerTaskManager, settings:Settings) = taskMgr.getPendingTaskCount match {
    case Success(count)=>
      println(s"Got $count running tasks")
      if(count>settings.maxRunningTasks){
        println(s"Limiting to ${settings.maxRunningTasks}, sending to flood queue")
        false
      } else {
        true
      }
    case Failure(err)=>
      println(s"ERROR: Could not check pending task count; $err")
      if(err.isInstanceOf[AmazonECSException]){

      }
      false
  }

  /**
    * checks whether we should process a request or send it to the flood queue
    * @param model RequestModel indicating the job to process
    * @param settings lambda settings
    * @param taskMgr ContainerTaskManagerInstance
    * @return a boolean. True if we should process immediately, false if we should send to flood queue
    */
  def checkEcsCapacity(model:RequestModel, settings:Settings, taskMgr:ContainerTaskManager) = {
    model.requestType match {
      case RequestType.ANALYSE=>
        checkTaskCount(taskMgr,settings)
      case RequestType.THUMBNAIL=>
        checkTaskCount(taskMgr,settings)
      case _=>  //nothing else requires ECS
        true
    }
  }

  /**
    * perform the request that we have been asked to
    * @param model RequestModel desribing the request to perform
    * @return a Future containing either the task ARN or a string with an error message
    */
  def processRequest(model: RequestModel, settings:Settings, taskMgr:ContainerTaskManager):Future[Either[String,String]] = Future {
    println(s"Processing request $model with reply topic ${settings.replyTopic}")
    model.requestType match {
      case RequestType.SETUP_PIPELINE=>
        println("Received setup pipeline request")
        implicit val etsClient = getEtsClient
        model.createPipelineRequest match {
          case None=>
            val replyMsg = MainAppReply.withPlainLog(JobReportStatus.FAILURE,None,model.jobId,"",Some("No pipeline request in message body"),None,None)
            snsClient.publish(new PublishRequest().withMessage(replyMsg.asJson.toString()).withTopicArn(settings.replyTopic))
            Left("No pipeline request in message body")
          case Some(pipelineRequest)=>
            etsPipelineManager.findPipelineFor(pipelineRequest.fromBucket, pipelineRequest.toBucket) match {
              case Failure(err)=>
                println(s"Could not look up existing pipelines: ${err.toString}")
                val replyMsg = MainAppReply.withPlainLog(JobReportStatus.FAILURE,None,model.jobId,"",Some(err.toString),None,None)
                snsClient.publish(new PublishRequest().withMessage(replyMsg.asJson.toString()).withTopicArn(settings.replyTopic))
                Left(err.toString)
              case Success(pipelines)=>
                if(pipelines.isEmpty){
                  println(s"found no existing pipelines for ${pipelineRequest.fromBucket}->${pipelineRequest.toBucket}, creating a new one")
                  doSetupPipeline(model, pipelineRequest.fromBucket, pipelineRequest.toBucket, settings)
                } else {
                  println(s"Found existing pipelines for ${pipelineRequest.fromBucket}->${pipelineRequest.toBucket}:")
                  pipelines.foreach(pl=>println(s"\t${pl.getName}: ${pl.getId} (${pl.getStatus}"))
                  if(model.hasForce){
                    println(s"Force option present, deleting existing ones and re-creating")
                    pipelines.foreach(pl=> {
                      val deleteRq = new DeletePipelineRequest().withId(pl.getId)
                      etsClient.deletePipeline(deleteRq)
                    })
                    doSetupPipeline(model, pipelineRequest.fromBucket, pipelineRequest.toBucket, settings)
                  } else {
                    println(s"Not deleting pipelines so I can't set up a new one.")
                    val replyMsg = MainAppReply.withPlainLog(JobReportStatus.FAILURE,None,model.jobId,"",Some(s"${pipelines.length} pipelines existing already, can't create. Retry with FORCE option to delete these."),None,None)
                    snsClient.publish(new PublishRequest().withMessage(replyMsg.asJson.toString()).withTopicArn(settings.replyTopic))
                    Left("Could not create pipeline")
                  }
                }
            }
        }

      case RequestType.THUMBNAIL=>
        taskMgr.runTask(
          command = Seq("/bin/bash","/usr/local/bin/extract_thumbnail.sh", URLDecoder.decode(model.inputMediaUri, "UTF-8"), URLDecoder.decode(model.targetLocation,"UTF-8"), settings.replyTopic, model.jobId),
          environment = Map(),
          name = s"extract_thumbnail_${model.jobId.toString}",
          cpu = None
        ) match {
          case Success(task)=>
            println(s"Successfully launched task: ${task.getTaskArn}")
            Right(task.getTaskArn)
          case Failure(err)=>
            println(s"Could not launch task: $err")
            Left(err.toString)
        }

      case RequestType.ANALYSE=>
        taskMgr.runTask(
          command = Seq("/usr/bin/python","/usr/local/bin/analyze_media_file.py", URLDecoder.decode(model.inputMediaUri, "UTF-8"), settings.replyTopic, model.jobId),
          environment = Map(),
          name = s"analyze_media_${model.jobId.toString}",
          cpu = None
        ) match {
          case Success(task)=>
            println(s"Successfully launched task: ${task.getTaskArn}")
            Right(task.getTaskArn)
          case Failure(err)=>
            println(s"Could not launch task: $err")
            Left(err.toString)
        }

      case RequestType.PROXY=>
        implicit val etsClient = getEtsClient
        val input = PathFunctions.breakdownS3Uri(model.inputMediaUri)
        val outputBucket = model.targetLocation
        val outputPrefix = PathFunctions.removeExtension(input._2) match {
          case Some(pfx)=>pfx
          case None=>input._2 //if there was no extension in the first place
        }

        println(s"Attempting to start transcode from $input to something with prefix s3://$outputBucket/$outputPrefix ...")
        val presetId = model.proxyType match {
          case Some(ProxyType.VIDEO)=>settings.videoPresetId
          case Some(ProxyType.AUDIO)=>settings.audioPresetId
          case Some(other)=>
            val err=s"Don't have a preset available for proxy type ${other.toString}"
            println(err)
            val msgReply = MainAppReply.withPlainLog(JobReportStatus.FAILURE,None,model.jobId,model.inputMediaUri,Some(err),Some(ProxyType.UNKNOWN),None)
            snsClient.publish(new PublishRequest().withMessage(msgReply.asJson.toString).withTopicArn(settings.replyTopic))
            throw new RuntimeException(s"No preset available for ${other.toString}")
          case None=>
            val err=s"ERROR: No ProxyType parameter in request"
            println(err)
            val msgReply = MainAppReply.withPlainLog(JobReportStatus.FAILURE,None,model.jobId,model.inputMediaUri,Some(err),None,None)
            snsClient.publish(new PublishRequest().withMessage(msgReply.asJson.toString).withTopicArn(settings.replyTopic))
            throw new RuntimeException(err)
        }

        etsPipelineManager.findPipelineFor(input._1,outputBucket).flatMap(pipelineList=>{
          if(pipelineList.isEmpty){
            println(s"No pipelines found for ${input._1} -> $outputBucket.")
            Failure(new RuntimeException("No pipeline available to process this media"))
          } else {
            println(s"Starting job on pipeline ${pipelineList.head}")
            Try { etsPipelineManager.makeJobRequest(input._2,outputPrefix, presetId,pipelineList.head.getId,model.jobId, model.proxyType.get) }
          }
        }) match {
          case Failure(err)=>
            println(s"Unable to start transcoding job: $err")
            checkETSShouldFloodqueue(err) match {
              case true=>
                sendToFloodQueue(getSqsClient,model,settings.floodQueue) match {
                  case Success(result)=>
                    println("Rate limiting detected, dispatching to flood queue")
                    Left("dispatched to flood queue")
                  case Failure(qError)=>
                    println(s"Could not dispatch to flood queue: ${qError.toString}")
                    Left(qError.toString)
                }
              case false=>
                val msgReply = MainAppReply.withPlainLog(JobReportStatus.FAILURE,None,model.jobId,model.inputMediaUri,Some(s"Unable to start transcoding job: $err"),model.proxyType,None)
                snsClient.publish(new PublishRequest().withMessage(msgReply.asJson.toString).withTopicArn(settings.replyTopic))
                Left(err.toString)
            }
          case Success(_)=>
            Right("Starting transcoding job")
        }

      case RequestType.CHECK_SETUP=>
        implicit val etsClient = getEtsClient
        //Expecting inputMediaUri to be either the bucket name or an s3://{bucket-name} uri
        println(s"Checking setup for ${model.inputMediaUri} -> ${model.targetLocation}")
        val inputBucket = PathFunctions.breakdownS3Uri(model.inputMediaUri)._1
        val outputBucket = model.targetLocation
        etsPipelineManager.findPipelineFor(inputBucket,outputBucket) match {
          case Success(pipelineSeq)=>
            if(pipelineSeq.isEmpty){
              val msgReply = MainAppReply.withPlainLog(JobReportStatus.FAILURE,pipelineSeq.headOption.map(_.getName),model.jobId,model.inputMediaUri,Some("No pipelines found for this target"),None,None)
              snsClient.publish(new PublishRequest().withMessage(msgReply.asJson.toString()).withTopicArn(settings.replyTopic))
              Left("Found no pipelines")
            } else {
              val logString = s"Found pipelines for ${model.inputMediaUri} -> ${model.targetLocation}: \n" + pipelineSeq.foldLeft("")((acc, entry) => acc + s"${entry.getName}: ${entry.getId} (${entry.getStatus})")

              etsPipelineManager.checkPipelineMessagingConfig(pipelineSeq.head.getId,settings.etsMessageTopic) match {
                case Failure(err)=>
                  val msgReply = MainAppReply.withPlainLog(JobReportStatus.FAILURE, pipelineSeq.headOption.map(_.getName), model.jobId, model.inputMediaUri, Some(err.toString), None, None)
                  snsClient.publish(new PublishRequest().withMessage(msgReply.asJson.toString()).withTopicArn(settings.replyTopic))
                case Success(Left(problem))=>
                  val msgReply = MainAppReply.withPlainLog(JobReportStatus.FAILURE, pipelineSeq.headOption.map(_.getName), model.jobId, model.inputMediaUri, Some(problem), None, None)
                  snsClient.publish(new PublishRequest().withMessage(msgReply.asJson.toString()).withTopicArn(settings.replyTopic))
                case Success(Right(value))=>
                  val msgReply = if(pipelineSeq.length>1) {
                    MainAppReply.withPlainLog(JobReportStatus.WARNING, pipelineSeq.headOption.map(_.getName), model.jobId, model.inputMediaUri, Some(s"Expected one pipeline, found ${pipelineSeq.length}"), None, None)
                  } else {
                    MainAppReply.withPlainLog(JobReportStatus.SUCCESS, pipelineSeq.headOption.map(_.getName), model.jobId, model.inputMediaUri, Some(logString), None, None)
                  }
                  snsClient.publish(new PublishRequest().withMessage(msgReply.asJson.toString()).withTopicArn(settings.replyTopic))
              }
              Right(s"Found ${pipelineSeq.length} pipelines")
            }
          case Failure(err)=>
            val msgReply = MainAppReply.withPlainLog(JobReportStatus.FAILURE,None,model.jobId,model.inputMediaUri,Some(err.toString),None,None)
            snsClient.publish(new PublishRequest().withMessage(msgReply.asJson.toString()).withTopicArn(settings.replyTopic))
            Left(err.toString)
        }
      case _=>
        val err=s"Don't understand requested action ${model.requestType}"
        val msgReply = MainAppReply.withPlainLog(JobReportStatus.FAILURE,None,model.jobId,model.inputMediaUri,Some(err),None,None)
        snsClient.publish(new PublishRequest().withMessage(msgReply.asJson.toString).withTopicArn(settings.replyTopic))
        Left(err)
    }
  }

  def getSettings:Settings = {
    Settings(
      sys.env.get("CLUSTER_NAME") match {
        case Some(str)=>str
        case None=>throw new RuntimeException("You need to specify CLUSTER_NAME")
      },
      sys.env.get("TASK_DEFINITION") match {
        case Some(str)=>str
        case None=>throw new RuntimeException("You need to specify TASK_DEFINITION")
      },
      sys.env.get("TASK_CONTAINER") match {
        case Some(str)=>str
        case None=>throw new RuntimeException("You need to specify TASK_CONTAINER")
      },
      sys.env.get("SUBNET_LIST").map(_.split(",")),
      sys.env.get("REPLY_TOPIC_ARN") match {
        case Some(str)=>str
        case None=>throw new RuntimeException("You need to specify REPLY_TOPIC_ARN")
      },
      sys.env.get("ETS_ROLE_ARN") match {
        case Some(str)=>str
        case None=>throw new RuntimeException("You need to specify ETS_ROLE_ARN")
      },
      sys.env.get("ETS_MESSAGE_TOPIC") match {
        case Some(str)=>str
        case None=>throw new RuntimeException("You need to specify ETS_MESSAGE_TOPIC")
      },
      sys.env.get("VIDEO_PRESET_ID") match {
        case Some(str)=>str
        case None=>throw new RuntimeException("You need to specify VIDEO_PRESET_ID")
      },
      sys.env.get("AUDIO_PRESET_ID") match {
        case Some(str)=>str
        case None=>throw new RuntimeException("You need to specify AUDIO_PRESET_ID")
      },
      sys.env.get("FLOOD_QUEUE") match {
        case Some(str)=>str
        case None=>throw new RuntimeException("You need to specify FLOOD_QUEUE")
      },
      sys.env.get("MAX_RUNNING_TASKS").map(_.toInt).getOrElse(50)
    )
  }

  /**
    * return a randomised number suitable for message delay
    * @return
    */
  def randomDelay:Int = {
    val upperLimit = 600
    val lowerLimit = 60
    val rnd = new scala.util.Random
    lowerLimit + rnd.nextInt((upperLimit - lowerLimit)+1)
  }

  override def handleRequest(evt: SNSEvent, context: Context): Unit = {
    val maybeReqeustsList = evt.getRecords.asScala.map(rec=>{
      io.circe.parser.parse(rec.getSNS.getMessage).flatMap(_.as[RequestModel])
    })
    val sqsClient = getSqsClient
    val failures = maybeReqeustsList.collect({case Left(err)=>err})

    if(failures.nonEmpty){
      failures.foreach(err=>{
        println(s"Could not decode request: ${err.toString}")
      })
    }

    val settings = getSettings

    val taskMgr = new ContainerTaskManager(settings.clusterName,settings.taskDefinitionName,settings.taskContainerName, settings.subnets)
    val initialRequestList = maybeReqeustsList.collect({case Right(rq)=>rq})

    val requestsForNow = initialRequestList.filter(rq=>{
      (checkEcsCapacity(rq, settings,taskMgr): @switch) match {
        case true=>true
        case false=>
          sendToFloodQueue(sqsClient,rq,settings.floodQueue)
          false
      }
    })

    val toWaitFor = Future.sequence(
      requestsForNow.map(rq=>
        processRequest(rq, settings, taskMgr).recover({
          case err:Throwable=>
            if(err.getMessage.contains("Rate limit exceeded") ||
              err.getMessage.contains("Throttling exception") ||
              err.getMessage.contains("no tasks started")
            ){
              println("WARNING: requests are being throttled. Sending this message onto the flood queue.")
              sendToFloodQueue(sqsClient,rq,settings.floodQueue)
            }
        })
      )
    )

    val results = Await.result(toWaitFor, 60.seconds)

    val runFailures = results.collect({case Left(err)=>err})
    if(runFailures.nonEmpty){
      println("Got the following errors triggering commands: ")
      runFailures.foreach(err=>println(s"\t$err"))
    }
    println(s"${runFailures.length} out of ${results.length} failed, the rest succeeded")
    if(runFailures.nonEmpty) throw new RuntimeException("Some jobs failed to process, see logs")
  }
}
