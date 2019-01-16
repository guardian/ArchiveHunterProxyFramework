import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.SNSEvent
import com.amazonaws.services.sns.AmazonSNSAsyncClientBuilder
import com.amazonaws.services.sns.model.PublishRequest

import scala.collection.JavaConverters._
import io.circe.syntax._
import io.circe.generic.auto._

class ReplyLambdaMain extends RequestHandler[SNSEvent, Unit] with TranscoderMessageDecoder {
  val snsClient = AmazonSNSAsyncClientBuilder.defaultClient()

  def getReplyTopic = sys.env.get("REPLY_TOPIC_ARN") match {
    case Some(str)=>str
    case None=>throw new RuntimeException("You need to specify REPLY_TOPIC_ARN")
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
          msg.userMetadata.flatMap(_.get("archivehunter-job-id")) match {
            case Some(jobId)=>
              val maybeReplyMsg = msg.state match {
                case TranscoderState.PROGRESSING=>
                  Some(MainAppReply.withPlainLog("running",None,jobId,"",None))
                case TranscoderState.COMPLETED=>
                  val maybeOutputPath = msg.outputs.flatMap(outList=>
                    outList.headOption.map(out=>out.key)
                  )
                  maybeOutputPath match {
                    case Some(outputPath)=>
                      Some(MainAppReply.withPlainLog("success",Some(outputPath),jobId,"",msg.messageDetails))
                    case None=>
                      println("ERROR: Success message with no outputs? This shouldn't happen.")
                      None
                  }
                case TranscoderState.ERROR=>
                  Some(MainAppReply.withPlainLog("error",None,jobId,"",msg.messageDetails))
                case TranscoderState.WARNING=>
                  Some(MainAppReply.withPlainLog("warning",None,jobId,"",msg.messageDetails))
              }

              maybeReplyMsg match {
                case Some(replyMsg) =>
                  try {
                    val prq = new PublishRequest().withTopicArn(replyTopic).withMessage(replyMsg.asJson.toString())
                    val result = snsClient.publish(prq)
                    println(s"Message sent with ${result.getMessageId}")
                  } catch {
                    case err: Throwable =>
                      println(s"ERROR: Couldn't send message: ${err.toString}")
                  }
                case None =>

              }
          }
      }
    })
  }
}
