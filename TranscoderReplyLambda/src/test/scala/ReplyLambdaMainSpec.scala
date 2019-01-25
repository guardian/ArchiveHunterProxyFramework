import com.amazonaws.services.elastictranscoder.model.Pipeline
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SNSEvent
import com.amazonaws.services.sns.AmazonSNSAsync
import com.amazonaws.services.sns.model.{PublishRequest, PublishResult}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import io.circe.syntax._
import io.circe.generic.auto._

import scala.collection.JavaConverters._

class ReplyLambdaMainSpec extends Specification with Mockito with RequestModelEncoder with TranscoderMessageDecoder with JobReportStatusEncoder {
  "ReplyLambdaMain.processMessage" should {
    "send a 'running' message to SNS for a PROGRESSING state" in {
      val mockedSnsClient = mock[AmazonSNSAsync]
      mockedSnsClient.publish(any[PublishRequest]) returns new PublishResult().withMessageId("message-id")
      val userMeta = Map(
        "archivehunter-job-id"->"jobuuid",
        "proxy-type"->"VIDEO"
      )

      val fakeMsg = AwsElasticTranscodeMsg(TranscoderState.PROGRESSING,"job-id","pipeline-id",None,None,None,Some(userMeta),None)
      val main = new ReplyLambdaMain {
        override val snsClient: AmazonSNSAsync = mockedSnsClient
        override def getReplyTopic: String = "fake-reply-topic"
      }

      val result = main.processMessage(fakeMsg,"reply-topic")
      val expectedOutputMsg = MainAppReply(JobReportStatus.RUNNING,None,"jobuuid","",None,Some(ProxyType.VIDEO),None)
      val expectedPublishRequest = new PublishRequest().withTopicArn("reply-topic").withMessage(expectedOutputMsg.asJson.toString)
      there was one(mockedSnsClient).publish(expectedPublishRequest)
      result must beRight("message-id")
    }

    "send an 'error' message to SNS for a FAILED state" in {
      val mockedSnsClient = mock[AmazonSNSAsync]
      mockedSnsClient.publish(any[PublishRequest]) returns new PublishResult().withMessageId("message-id")
      val userMeta = Map(
        "archivehunter-job-id"->"jobuuid",
        "proxy-type"->"VIDEO"
      )

      val fakeMsg = AwsElasticTranscodeMsg(TranscoderState.ERROR,"job-id","pipeline-id",None,Some(6001),Some("supacalfragelisticexpialedocious"),Some(userMeta),None)
      val main = new ReplyLambdaMain {
        override val snsClient: AmazonSNSAsync = mockedSnsClient
        override def getReplyTopic: String = "fake-reply-topic"
      }

      val result = main.processMessage(fakeMsg,"reply-topic")
      val expectedOutputMsg = MainAppReply(JobReportStatus.FAILURE,None,"jobuuid","",Some("c3VwYWNhbGZyYWdlbGlzdGljZXhwaWFsZWRvY2lvdXM="),Some(ProxyType.VIDEO),None)
      val expectedPublishRequest = new PublishRequest().withTopicArn("reply-topic").withMessage(expectedOutputMsg.asJson.toString)
      there was one(mockedSnsClient).publish(expectedPublishRequest)
      result must beRight("message-id")
    }

    "send an 'warning' message to SNS for a WARNING state" in {
      val mockedSnsClient = mock[AmazonSNSAsync]
      mockedSnsClient.publish(any[PublishRequest]) returns new PublishResult().withMessageId("message-id")
      val userMeta = Map(
        "archivehunter-job-id"->"jobuuid",
        "proxy-type"->"VIDEO"
      )

      val fakeMsg = AwsElasticTranscodeMsg(TranscoderState.WARNING,"job-id","pipeline-id",None,None,Some("supacalfragelisticexpialedocious"),Some(userMeta),None)
      val main = new ReplyLambdaMain {
        override val snsClient: AmazonSNSAsync = mockedSnsClient
        override def getReplyTopic: String = "fake-reply-topic"
      }

      val result = main.processMessage(fakeMsg,"reply-topic")
      val expectedOutputMsg = MainAppReply(JobReportStatus.WARNING,None,"jobuuid","",Some("c3VwYWNhbGZyYWdlbGlzdGljZXhwaWFsZWRvY2lvdXM="),Some(ProxyType.VIDEO),None)
      val expectedPublishRequest = new PublishRequest().withTopicArn("reply-topic").withMessage(expectedOutputMsg.asJson.toString)
      there was one(mockedSnsClient).publish(expectedPublishRequest)
      result must beRight("message-id")
    }

    "send a 'success' message to SNS for a COMPLETED state, including the output path" in {
      val mockedSnsClient = mock[AmazonSNSAsync]
      mockedSnsClient.publish(any[PublishRequest]) returns new PublishResult().withMessageId("message-id")
      val userMeta = Map(
        "archivehunter-job-id"->"jobuuid",
        "proxy-type"->"VIDEO"
      )

      val expectedOutput = ETSOutput("output-id","preset-id","COMPLETED",Some(100L),Some(123456L),Some(1920),Some(1080),"outfile.mp4")
      val fakeMsg = AwsElasticTranscodeMsg(TranscoderState.COMPLETED,"job-id","pipeline-id",None,None,Some("supacalfragelisticexpialedocious"),Some(userMeta),Some(Seq(expectedOutput)))
      val main = new ReplyLambdaMain {
        override val snsClient: AmazonSNSAsync = mockedSnsClient
        override def getReplyTopic: String = "fake-reply-topic"

        override def getPipelineConfig(pipelineId: String): Option[Pipeline] = Some(new Pipeline().withOutputBucket("proxybucket"))
      }

      val result = main.processMessage(fakeMsg,"reply-topic")

      val expectedOutputMsg = MainAppReply(JobReportStatus.SUCCESS,Some("s3://proxybucket/outfile.mp4"),"jobuuid","",Some("c3VwYWNhbGZyYWdlbGlzdGljZXhwaWFsZWRvY2lvdXM="),Some(ProxyType.VIDEO),None)
      val expectedPublishRequest = new PublishRequest().withTopicArn("reply-topic").withMessage(expectedOutputMsg.asJson.toString)
      there was one(mockedSnsClient).publish(expectedPublishRequest)
      result must beRight("message-id")
    }

    "error if a completed message has no output section" in {
      val mockedSnsClient = mock[AmazonSNSAsync]
      mockedSnsClient.publish(any[PublishRequest]) returns new PublishResult().withMessageId("message-id")
      val userMeta = Map(
        "archivehunter-job-id"->"jobuuid",
        "proxy-type"->"VIDEO"
      )

      val fakeMsg = AwsElasticTranscodeMsg(TranscoderState.COMPLETED,"job-id","pipeline-id",None,None,Some("supacalfragelisticexpialedocious"),Some(userMeta),None)
      val main = new ReplyLambdaMain {
        override val snsClient: AmazonSNSAsync = mockedSnsClient
        override def getReplyTopic: String = "fake-reply-topic"
      }

      val result = main.processMessage(fakeMsg,"reply-topic")

      there was no(mockedSnsClient).publish(any[PublishRequest])
      result must beLeft("Nothing to relay to main app")
    }

    "error if the message has no custom job-id in the metadata" in {
      val mockedSnsClient = mock[AmazonSNSAsync]
      mockedSnsClient.publish(any[PublishRequest]) returns new PublishResult().withMessageId("message-id")
      val userMeta = Map(
        "proxy-type"->"VIDEO"
      )

      val fakeMsg = AwsElasticTranscodeMsg(TranscoderState.COMPLETED,"job-id","pipeline-id",None,None,Some("supacalfragelisticexpialedocious"),Some(userMeta),None)
      val main = new ReplyLambdaMain {
        override val snsClient: AmazonSNSAsync = mockedSnsClient
        override def getReplyTopic: String = "fake-reply-topic"
      }

      val result = main.processMessage(fakeMsg,"reply-topic")

      there was no(mockedSnsClient).publish(any[PublishRequest])
      result must beLeft("No Archivehunter job ID in the metadata! this shouldn't happen.")
    }
  }

  "ReplyLambdaMain.handleRequest" should {
    "parse out a valid message and call processMessage for each event within it" in {
      val mockedSnsClient = mock[AmazonSNSAsync]
      val mockedProcessMessage = mock[Function2[AwsElasticTranscodeMsg, String, Either[String,String]]]
      val mockedContext = mock[Context]
      val msg1 = AwsElasticTranscodeMsg(TranscoderState.PROGRESSING,"job-id-1","pipeline-id-1",None,None,None,None,None)
      val msg2 = AwsElasticTranscodeMsg(TranscoderState.PROGRESSING,"job-id-2","pipeline-id-2",None,None,None,None,None)
      val incoming = new SNSEvent().withRecords(Seq(
        new SNSEvent.SNSRecord().withSns(new SNSEvent.SNS().withMessage(msg1.asJson.toString())),
        new SNSEvent.SNSRecord().withSns(new SNSEvent.SNS().withMessage(msg2.asJson.toString()))
      ).asJava)

      val main = new ReplyLambdaMain {
        override val snsClient = mockedSnsClient

        override def getReplyTopic: String = "fake-reply-topic"

        override def processMessage(msg: AwsElasticTranscodeMsg, replyTopic: String): Either[String, String] = mockedProcessMessage(msg, replyTopic)
      }

      main.handleRequest(incoming,mockedContext)
      there was one(mockedProcessMessage).apply(msg1,"fake-reply-topic")
      there was one(mockedProcessMessage).apply(msg2,"fake-reply-topic")
    }
  }
}
