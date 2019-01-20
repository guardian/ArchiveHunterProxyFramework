import com.amazonaws.services.ecs.{AmazonECS, AmazonECSClientBuilder}
import com.amazonaws.services.elastictranscoder.{AmazonElasticTranscoder, AmazonElasticTranscoderClientBuilder}
import com.amazonaws.services.elastictranscoder.model._
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.SNSEvent
import com.amazonaws.services.sns.AmazonSNSClientBuilder
import com.amazonaws.services.sns.model.PublishRequest

import scala.collection.JavaConverters._
import io.circe.syntax._
import io.circe.generic.auto._

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import scala.concurrent.duration._

class RequestLambdaMain extends RequestHandler[SNSEvent,Unit] with RequestModelEncoder {
  def getSnsClient = AmazonSNSClientBuilder.defaultClient()
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

  def doSetupPipeline(model:RequestModel, settings:Settings)(implicit etsClient:AmazonElasticTranscoder) = {
    val rq = model.createPipelineRequest.get
    val pipelineName = s"archivehunter_${randomAlphaNumericString(10)}"
    println(s"Attempting to create a pipeline request with name $pipelineName")
    val createRq = new CreatePipelineRequest()
      .withInputBucket(rq.fromBucket)
      .withName(pipelineName)
      .withNotifications(new Notifications().withCompleted(settings.etsMessageTopic).withError(settings.etsMessageTopic).withWarning(settings.etsMessageTopic).withProgressing(settings.etsMessageTopic))
      .withOutputBucket(rq.toBucket)
      .withRole(settings.etsRoleArn)
    etsPipelineManager.createEtsPipeline(createRq,settings.etsRoleArn)
      .flatMap(pipeline=>etsPipelineManager.waitForCompletion(pipeline.getId)) match {
      case Success(pipelineId)=>
        println(s"Successfully created pipeline: $pipelineId")
        val replyMsg = MainAppReply.withPlainLog("success",Some(pipelineId),model.jobId,"",None,None,None)
        snsClient.publish(new PublishRequest().withMessage(replyMsg.asJson.toString()).withTopicArn(settings.replyTopic))
        Right("Created pipeline")
      case Failure(err)=>
        println(s"Could not create pipeline: $err")
        val replyMsg = MainAppReply.withPlainLog("error",None,model.jobId,"",Some(err.toString),None,None)
        snsClient.publish(new PublishRequest().withMessage(replyMsg.asJson.toString()).withTopicArn(settings.replyTopic))
        Left("Could not create pipeline")
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
        doSetupPipeline(model, settings)

      case RequestType.THUMBNAIL=>
        taskMgr.runTask(
          command = Seq("/bin/bash","/usr/local/bin/extract_thumbnail.sh", model.inputMediaUri, model.targetLocation, settings.replyTopic, model.jobId),
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
          command = Seq("/usr/bin/python","/usr/local/bin/analyze_media_file.py", model.inputMediaUri, settings.replyTopic, model.jobId),
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
      case RequestType.PROXY=>
        implicit val etsClient = getEtsClient
        val input = PathFunctions.breakdownS3Uri(model.inputMediaUri)
        val output = PathFunctions.breakdownS3Uri(model.targetLocation)

        println(s"Attempting to start transcode from $input to $output")
        val presetId = model.proxyType match {
          case Some(ProxyType.VIDEO)=>settings.videoPresetId
          case Some(ProxyType.AUDIO)=>settings.audioPresetId
          case Some(other)=>
            val err=s"Don't have a preset available for proxy type ${other.toString}"
            println(err)
            val msgReply = MainAppReply.withPlainLog("error",None,model.jobId,model.inputMediaUri,Some(err),Some(ProxyType.UNKNOWN),None)
            snsClient.publish(new PublishRequest().withMessage(msgReply.asJson.toString).withTopicArn(settings.replyTopic))
            throw new RuntimeException(s"No preset available for ${other.toString}")
          case None=>
            val err=s"ERROR: No ProxyType parameter in request"
            println(err)
            val msgReply = MainAppReply.withPlainLog("error",None,model.jobId,model.inputMediaUri,Some(err),None,None)
            snsClient.publish(new PublishRequest().withMessage(msgReply.asJson.toString).withTopicArn(settings.replyTopic))
            throw new RuntimeException(err)
        }

        etsPipelineManager.findPipelineFor(input._1,output._1).flatMap(pipelineList=>{
          if(pipelineList.isEmpty){
            println(s"No pipelines found for ${input._1} -> ${output._1}.")
            Failure(new RuntimeException("No pipeline available to process this media"))
          } else {
            println(s"Starting job on pipeline ${pipelineList.head}")
            Success(etsPipelineManager.makeJobRequest(input._2,output._2, presetId,pipelineList.head.getId,model.jobId, model.proxyType.get))
          }
        }) match {
          case Failure(err)=>
            println(s"Unable to start transcoding job: $err")
            val msgReply = MainAppReply.withPlainLog("error",None,model.jobId,model.inputMediaUri,Some(s"Unable to start transcoding job: $err"),model.proxyType,None)
            snsClient.publish(new PublishRequest().withMessage(msgReply.asJson.toString).withTopicArn(settings.replyTopic))
            Left(err.toString)
          case Success(_)=>
            Right("Starting transcoding job")
        }
      case _=>
        val err=s"Don't understand requested action ${model.requestType}"
        val msgReply = MainAppReply.withPlainLog("error",None,model.jobId,model.inputMediaUri,Some(err),None,None)
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
      }

    )
  }

  override def handleRequest(evt: SNSEvent, context: Context): Unit = {
    val maybeReqeustsList = evt.getRecords.asScala.map(rec=>{
      io.circe.parser.parse(rec.getSNS.getMessage).flatMap(_.as[RequestModel])
    })

    val failures = maybeReqeustsList.collect({case Left(err)=>err})

    if(failures.nonEmpty){
      failures.foreach(err=>{
        println(s"Could not decode request: ${err.toString}")
      })
    }

    val settings = getSettings

    val taskMgr = new ContainerTaskManager(settings.clusterName,settings.taskDefinitionName,settings.taskContainerName, settings.subnets)
    val requests = maybeReqeustsList.collect({case Right(rq)=>rq})
    val toWaitFor = Future.sequence(requests.map(rq=>processRequest(rq, settings, taskMgr)))

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
