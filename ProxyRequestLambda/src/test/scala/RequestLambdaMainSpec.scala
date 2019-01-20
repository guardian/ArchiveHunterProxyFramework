import com.amazonaws.services.ecs.AmazonECS
import com.amazonaws.services.ecs.model.Task
import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoder
import com.amazonaws.services.elastictranscoder.model.Pipeline
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SNSEvent
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model.{PublishRequest, PublishResult}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import io.circe.syntax._
import io.circe.generic.auto._

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.collection.JavaConverters._

class RequestLambdaMainSpec extends Specification with Mockito with RequestModelEncoder {
  "RequestLambdaMain.processRequest" should {
    "call out to taskMgr.RunTask for a THUMBNAIL action" in {
      val mockedTaskMgr = mock[ContainerTaskManager]
      val mockedEcsClient = mock[AmazonECS]
      val mockedSettings = mock[Settings]
      val fakeJobDesc = new Task().withTaskArn("fake-task-arn")
      val fakeRequest = RequestModel(RequestType.THUMBNAIL,"s3://fake-media-uri","fake-target-location","fake-job-id",None,None)
      mockedTaskMgr.runTask(any,any,any,any)(any) returns Success(fakeJobDesc)

      val toTest = new RequestLambdaMain {
        override def getEcsClient: AmazonECS = mockedEcsClient
      }
      val result = Await.result(toTest.processRequest(fakeRequest,mockedSettings,mockedTaskMgr), 5 seconds)

      there was one(mockedTaskMgr).runTask(any,any,any,any)(any)
      result must beRight("fake-task-arn")
    }

    "return left if RunTask signifies failure" in {
      val mockedTaskMgr = mock[ContainerTaskManager]
      val mockedEcsClient = mock[AmazonECS]
      val mockedSettings = mock[Settings]
      val fakeJobDesc = new Task().withTaskArn("fake-task-arn")
      val fakeRequest = RequestModel(RequestType.THUMBNAIL,"s3://fake-media-uri","fake-target-location","fake-job-id",None,None)
      mockedTaskMgr.runTask(any,any,any,any)(any) returns Failure(new RuntimeException("My hovercraft is full of eels"))

      val toTest = new RequestLambdaMain {
        override def getEcsClient: AmazonECS = mockedEcsClient
      }
      val result = Await.result(toTest.processRequest(fakeRequest,mockedSettings,mockedTaskMgr), 5 seconds)

      there was one(mockedTaskMgr).runTask(any,any,any,any)(any)
      result must beLeft("java.lang.RuntimeException: My hovercraft is full of eels")
    }

    "call out to taskMgr.RunTask for an ANALYSE action" in {
      val mockedTaskMgr = mock[ContainerTaskManager]
      val mockedEcsClient = mock[AmazonECS]
      val mockedSettings = mock[Settings]
      val fakeJobDesc = new Task().withTaskArn("fake-task-arn")
      val fakeRequest = RequestModel(RequestType.ANALYSE,"s3://fake-media-uri","","fake-job-id",None,None)
      mockedTaskMgr.runTask(any,any,any,any)(any) returns Success(fakeJobDesc)

      val toTest = new RequestLambdaMain {
        override def getEcsClient: AmazonECS = mockedEcsClient
      }
      val result = Await.result(toTest.processRequest(fakeRequest,mockedSettings,mockedTaskMgr), 5 seconds)

      there was one(mockedTaskMgr).runTask(any,any,any,any)(any)
      result must beRight("fake-task-arn")
    }

    "return left if RunTask signifies failure" in {
      val mockedTaskMgr = mock[ContainerTaskManager]
      val mockedEcsClient = mock[AmazonECS]
      val mockedSettings = mock[Settings]
      val fakeJobDesc = new Task().withTaskArn("fake-task-arn")
      val fakeRequest = RequestModel(RequestType.ANALYSE,"s3://fake-media-uri","fake-target-location","fake-job-id",None,None)
      mockedTaskMgr.runTask(any,any,any,any)(any) returns Failure(new RuntimeException("My hovercraft is full of eels"))

      val toTest = new RequestLambdaMain {
        override def getEcsClient: AmazonECS = mockedEcsClient
      }
      val result = Await.result(toTest.processRequest(fakeRequest,mockedSettings,mockedTaskMgr), 5 seconds)

      there was one(mockedTaskMgr).runTask(any,any,any,any)(any)
      result must beLeft("java.lang.RuntimeException: My hovercraft is full of eels")
    }

    "call out to ETSPipelineManager to find a pipeline and start a transcode if a PROXY is requested" in {
      val mockedPipeline = mock[Pipeline]
      mockedPipeline.getId returns "fake-pipeline-id"

      val mockedPipelineManager = mock[ETSPipelineManager]
      mockedPipelineManager.findPipelineFor(any,any)(any) returns Success(Seq(mockedPipeline))
      mockedPipelineManager.makeJobRequest(any,any,any,any,any,any)(any) returns Right("job-id")
      val mockedTaskMgr = mock[ContainerTaskManager]
      val mockedEcsClient = mock[AmazonECS]
      val mockedEtsClient = mock[AmazonElasticTranscoder]
      val mockedSettings = mock[Settings]
      mockedSettings.videoPresetId returns "video-preset-id"
      mockedSettings.audioPresetId returns "audio-preset-id"

      val fakeRequest = RequestModel(RequestType.PROXY, "s3://mediabucket/fake-media-uri","s3://proxybucket/fake-proxy-uri","fake-job-id",None,Some(ProxyType.VIDEO))
      val toTest = new RequestLambdaMain {
        override def getEcsClient: AmazonECS = mockedEcsClient

        override def getEtsClient: AmazonElasticTranscoder = mockedEtsClient

        override val etsPipelineManager: ETSPipelineManager = mockedPipelineManager
      }

      val result = Await.result(toTest.processRequest(fakeRequest, mockedSettings, mockedTaskMgr), 5 seconds)
      there was one(mockedPipelineManager).findPipelineFor("mediabucket","proxybucket")(mockedEtsClient)
      there was no(mockedPipelineManager).createEtsPipeline(any,any)(any)
      there was one(mockedPipelineManager).makeJobRequest("fake-media-uri","fake-proxy-uri","video-preset-id","fake-pipeline-id","fake-job-id",ProxyType.VIDEO)(mockedEtsClient)
      result must beRight
    }

    "error if an applicable pipeline if can't be found, and start a transcode if a PROXY is requested" in {
      val mockedPipeline = mock[Pipeline]
      mockedPipeline.getId returns "fake-pipeline-id"

      val mockedPipelineManager = mock[ETSPipelineManager]
      mockedPipelineManager.findPipelineFor(any,any)(any) returns Success(Seq())
      mockedPipelineManager.makeJobRequest(any,any,any,any,any,any)(any) returns Right("job-id")
      mockedPipelineManager.createEtsPipeline(any,any)(any) returns Success(mockedPipeline)
      val mockedTaskMgr = mock[ContainerTaskManager]
      val mockedEcsClient = mock[AmazonECS]
      val mockedEtsClient = mock[AmazonElasticTranscoder]
      val mockedSnsClient = mock[AmazonSNS]
      mockedSnsClient.publish(any[PublishRequest]) returns mock[PublishResult]
      val mockedSettings = mock[Settings]
      mockedSettings.videoPresetId returns "video-preset-id"
      mockedSettings.audioPresetId returns "audio-preset-id"

      val fakeRequest = RequestModel(RequestType.PROXY, "s3://mediabucket/fake-media-uri","s3://proxybucket/fake-proxy-uri","fake-job-id",None,Some(ProxyType.VIDEO))
      val toTest = new RequestLambdaMain {
        override def getEcsClient: AmazonECS = mockedEcsClient

        override def getEtsClient: AmazonElasticTranscoder = mockedEtsClient

        override def getSnsClient: AmazonSNS = mockedSnsClient
        override val etsPipelineManager: ETSPipelineManager = mockedPipelineManager
      }

      val result = Await.result(toTest.processRequest(fakeRequest, mockedSettings, mockedTaskMgr), 5 seconds)
      there was one(mockedPipelineManager).findPipelineFor("mediabucket","proxybucket")(mockedEtsClient)
      there was one(mockedSnsClient).publish(any[PublishRequest])
      there was no(mockedPipelineManager).makeJobRequest("fake-media-uri","fake-proxy-uri","video-preset-id","fake-pipeline-id","fake-job-id",ProxyType.VIDEO)(mockedEtsClient)
      result must beLeft("java.lang.RuntimeException: No pipeline available to process this media")
    }
  }


  "RequestLambdaMain.handleRequest" should {
    "convert SNS messages into our message format and not choke on errors" in {
      val actualRequest = RequestModel(RequestType.ANALYSE,"input-uri","target-location","job-id",None,None)
      val recods = List(
        new SNSRecord().withSns(new SNSEvent.SNS().withMessage(actualRequest.asJson.toString)),
        new SNSRecord().withSns(new SNSEvent.SNS().withMessage("{\"field\":\"invalidmessage\"}")),
        new SNSRecord().withSns(new SNSEvent.SNS().withMessage("notevenjson")),
      )
      val evt = new SNSEvent().withRecords(recods.asJava)

      val mockProcessRecord = mock[Function3[RequestModel,Settings,ContainerTaskManager,Future[Either[String,String]]]]
      mockProcessRecord.apply(any,any,any) returns Future(Right("mock was called"))

      val toTest = new RequestLambdaMain {
        override def getEcsClient: AmazonECS = mock[AmazonECS]
        override def getSettings: Settings = Settings("fake-cluster-name","fake-task-def","fake-container-name",None,"fake-reply-topic","fake-role-arn","fake-topic","vpreset","apreset")

        override def processRequest(model: RequestModel, settings:Settings, taskMgr: ContainerTaskManager): Future[Either[String, String]] = mockProcessRecord(model, settings, taskMgr)
      }

      val result = toTest.handleRequest(evt, mock[Context])
      there was one(mockProcessRecord).apply(any,any,any)
    }
  }
}
