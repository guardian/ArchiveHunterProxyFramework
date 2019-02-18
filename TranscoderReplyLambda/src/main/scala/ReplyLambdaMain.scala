import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoderClientBuilder
import com.amazonaws.services.elastictranscoder.model.{AmazonElasticTranscoderException, Pipeline}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.SNSEvent
import com.amazonaws.services.sns.AmazonSNSAsyncClientBuilder
import com.amazonaws.services.sns.model.PublishRequest
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClientBuilder}
import com.amazonaws.services.sqs.model.SendMessageRequest

import scala.collection.JavaConverters._
import io.circe.syntax._
import io.circe.generic.auto._

import scala.util.{Failure, Success, Try}

class ReplyLambdaMain extends RequestHandler[SNSEvent, Unit] with TranscoderMessageDecoder with RequestModelEncoder with JobReportStatusEncoder {
  val snsClient = AmazonSNSAsyncClientBuilder.defaultClient()
  val etsClient = AmazonElasticTranscoderClientBuilder.defaultClient()

  def getSqsClient = AmazonSQSClientBuilder.defaultClient()

  def getReplyTopic = sys.env.get("REPLY_TOPIC_ARN") match {
    case Some(str)=>str
    case None=>throw new RuntimeException("You need to specify REPLY_TOPIC_ARN")
  }

  def getFloodQueue = sys.env.get("FLOOD_QUEUE") match {
    case Some(str)=>str
    case None=>throw new RuntimeException("You need to specify FLOOD_QUEUE")
  }

  def getPipelineConfig(pipelineId:String) = Try {
    val result = etsClient.listPipelines()
    result.getPipelines.asScala.find(_.getId==pipelineId)
  }

  /**
    * should the given ETS error be sent to the flood queue?
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

  /**
    * re-serialize the request and push it onto the flood queue.
    * @param sqsClient AmazonSQS instance
    * @param rq requestModel to send
    * @param floodQueue URL of the flood queue
    * @return a Try, with either the SendMessageResult or an error
    */
  protected def sendToFloodQueue(sqsClient:AmazonSQS, msg:AwsElasticTranscodeMsg, floodQueue:String) = Try {
    println("Sending message to flood queue")
    val sendRq = new SendMessageRequest()
      .withQueueUrl(floodQueue)
      .withMessageBody(msg.asJson.toString)
      .withDelaySeconds(randomDelay) //use a randomised delay so the messages don't all come back through at the same time

    sqsClient.sendMessage(sendRq)
  }

  def getOutputUri(plConfig:Pipeline,outputPath:String) = s"s3://${plConfig.getOutputBucket}/$outputPath"

  def getInputUri(plConfig:Pipeline,inputPath:String) = s"s3://${plConfig.getInputBucket}/$inputPath"

  def withOutputUri(msg:AwsElasticTranscodeMsg, jobId:String, maybeProxyType:Option[ProxyType.Value])(block:String=>Option[MainAppReply]) = {
    val maybeOutputPath = msg.outputs.flatMap(outList =>
      outList.headOption.map(out => out.key)
    )
    maybeOutputPath match {
      case Some(outputPath) =>
        getPipelineConfig(msg.pipelineId) match {
          case Success(None) =>
            println(s"ERROR: pipeline ${msg.pipelineId} could not be found. Has it been deleted?")
            Some(MainAppReply.withPlainLog(JobReportStatus.FAILURE, None, jobId, "", Some(s"ERROR: pipeline ${msg.pipelineId} could not be found. Has it been deleted?"), maybeProxyType, None))
          case Success(Some(plConfig)) =>
            val outputUri = getOutputUri(plConfig, outputPath)
            block(outputUri)
          case Failure(err) =>
            checkETSShouldFloodqueue(err) match {
              case true =>
                sendToFloodQueue(getSqsClient, msg, getFloodQueue)
                None
              case false =>
                throw err
            }
        }
      case None =>
        println("ERROR: Success message with no outputs? This shouldn't happen.")
        None
    }
  }

  def processMessage(msg:AwsElasticTranscodeMsg, replyTopic:String) = {
    val maybeProxyType = msg.userMetadata.flatMap(_.get("proxy-type").map(s=>ProxyType.withName(s)))
    if(maybeProxyType.isEmpty) println("WARNING: No proxy-type field for this job.")
    msg.userMetadata.flatMap(_.get("archivehunter-job-id")) match {
      case None=>
        println("No Archivehunter job ID in the metadata! this shouldn't happen.")
        Left("No Archivehunter job ID in the metadata! this shouldn't happen.")
      case Some(jobId)=>
        val maybeReplyMsg = msg.state match {
          case TranscoderState.PROGRESSING =>
            Some(MainAppReply.withPlainLog(JobReportStatus.RUNNING, None, jobId, "", None, maybeProxyType, None))
          case TranscoderState.COMPLETED =>
            withOutputUri(msg, jobId, maybeProxyType) { outputUri =>
              Some(MainAppReply.withPlainLog(JobReportStatus.SUCCESS, Some(outputUri), jobId, "", msg.messageDetails, maybeProxyType, None))
            }
          case TranscoderState.ERROR =>
            if (msg.errorCode.contains(3002)) {
              //3002 => output file already exists. Send a warning message with the output field set.
              withOutputUri(msg, jobId, maybeProxyType) { outputUri =>
                Some(MainAppReply.withPlainLog(JobReportStatus.WARNING, Some(outputUri), jobId, "", msg.messageDetails, maybeProxyType, None))
              }
            } else {
              Some(MainAppReply.withPlainLog(JobReportStatus.FAILURE, None, jobId, "", msg.messageDetails, maybeProxyType, None))
            }
          case TranscoderState.WARNING =>
            Some(MainAppReply.withPlainLog(JobReportStatus.WARNING, None, jobId, "", msg.messageDetails, maybeProxyType, None))
        }
        maybeReplyMsg match {
          case Some(replyMsg) =>
            try {
              val prq = new PublishRequest().withTopicArn(replyTopic).withMessage(replyMsg.asJson.toString())
              val result = snsClient.publish(prq)
              println(s"Message sent with ${result.getMessageId}")
              Right(result.getMessageId)
            } catch {
              case err: Throwable =>
                println(s"ERROR: Couldn't send message: ${err.toString}")
                Left(err.toString)
            }
          case None =>
            println("ERROR: Nothing to relay on to main app")
            Left("Nothing to relay to main app")
        }
    }
  }

  override def handleRequest(i: SNSEvent, context: Context): Unit = {
    val replyTopic = getReplyTopic

    i.getRecords.asScala.foreach(rec=>{
      println(s"Received message ${rec.getSNS.getMessage}")
      io.circe.parser.parse(rec.getSNS.getMessage).flatMap(_.as[AwsElasticTranscodeMsg]) match {
        case Left(err)=>
          println(s"Could not parse incoming message: ${err.toString}")
        case Right(msg)=>
          println(s"Got message: $msg")
          processMessage(msg, replyTopic)
      }
    })
  }
}
