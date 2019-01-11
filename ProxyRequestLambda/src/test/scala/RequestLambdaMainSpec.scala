import com.amazonaws.services.ecs.AmazonECS
import com.amazonaws.services.ecs.model.Task
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SNSEvent
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord
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
      val fakeJobDesc = new Task().withTaskArn("fake-task-arn")
      val fakeRequest = RequestModel(RequestType.THUMBNAIL,"s3://fake-media-uri","fake-target-location","fake-job-id")
      mockedTaskMgr.runTask(any,any,any,any)(any) returns Success(fakeJobDesc)

      val toTest = new RequestLambdaMain {
        override def getEcsClient: AmazonECS = mockedEcsClient
      }
      val result = Await.result(toTest.processRequest(fakeRequest,"fake-reply-topic",mockedTaskMgr), 5 seconds)

      there was one(mockedTaskMgr).runTask(any,any,any,any)(any)
      result must beRight("fake-task-arn")
    }

    "return left if RunTask signifies failure" in {
      val mockedTaskMgr = mock[ContainerTaskManager]
      val mockedEcsClient = mock[AmazonECS]
      val fakeJobDesc = new Task().withTaskArn("fake-task-arn")
      val fakeRequest = RequestModel(RequestType.THUMBNAIL,"s3://fake-media-uri","fake-target-location","fake-job-id")
      mockedTaskMgr.runTask(any,any,any,any)(any) returns Failure(new RuntimeException("My hovercraft is full of eels"))

      val toTest = new RequestLambdaMain {
        override def getEcsClient: AmazonECS = mockedEcsClient
      }
      val result = Await.result(toTest.processRequest(fakeRequest,"fake-reply-topic",mockedTaskMgr), 5 seconds)

      there was one(mockedTaskMgr).runTask(any,any,any,any)(any)
      result must beLeft("java.lang.RuntimeException: My hovercraft is full of eels")
    }

    "call out to taskMgr.RunTask for an ANALYSE action" in {
      val mockedTaskMgr = mock[ContainerTaskManager]
      val mockedEcsClient = mock[AmazonECS]
      val fakeJobDesc = new Task().withTaskArn("fake-task-arn")
      val fakeRequest = RequestModel(RequestType.ANALYSE,"s3://fake-media-uri","","fake-job-id")
      mockedTaskMgr.runTask(any,any,any,any)(any) returns Success(fakeJobDesc)

      val toTest = new RequestLambdaMain {
        override def getEcsClient: AmazonECS = mockedEcsClient
      }
      val result = Await.result(toTest.processRequest(fakeRequest,"fake-reply-topic",mockedTaskMgr), 5 seconds)

      there was one(mockedTaskMgr).runTask(any,any,any,any)(any)
      result must beRight("fake-task-arn")
    }

    "return left if RunTask signifies failure" in {
      val mockedTaskMgr = mock[ContainerTaskManager]
      val mockedEcsClient = mock[AmazonECS]
      val fakeJobDesc = new Task().withTaskArn("fake-task-arn")
      val fakeRequest = RequestModel(RequestType.ANALYSE,"s3://fake-media-uri","fake-target-location","fake-job-id")
      mockedTaskMgr.runTask(any,any,any,any)(any) returns Failure(new RuntimeException("My hovercraft is full of eels"))

      val toTest = new RequestLambdaMain {
        override def getEcsClient: AmazonECS = mockedEcsClient
      }
      val result = Await.result(toTest.processRequest(fakeRequest,"fake-reply-topic",mockedTaskMgr), 5 seconds)

      there was one(mockedTaskMgr).runTask(any,any,any,any)(any)
      result must beLeft("java.lang.RuntimeException: My hovercraft is full of eels")
    }
  }


  "RequestLambdaMain.handleRequest" should {
    "convert SNS messages into our message format and not choke on errors" in {
      val actualRequest = RequestModel(RequestType.ANALYSE,"input-uri","target-location","job-id")
      val recods = List(
        new SNSRecord().withSns(new SNSEvent.SNS().withMessage(actualRequest.asJson.toString)),
        new SNSRecord().withSns(new SNSEvent.SNS().withMessage("{\"field\":\"invalidmessage\"}")),
        new SNSRecord().withSns(new SNSEvent.SNS().withMessage("notevenjson")),
      )
      val evt = new SNSEvent().withRecords(recods.asJava)

      val mockProcessRecord = mock[Function3[RequestModel,String,ContainerTaskManager,Future[Either[String,String]]]]
      mockProcessRecord.apply(any,any,any) returns Future(Right("mock was called"))

      val toTest = new RequestLambdaMain {
        override def getEcsClient: AmazonECS = mock[AmazonECS]
        override def getSettings: Settings = Settings("fake-cluster-name","fake-task-def","fake-container-name",None,"fake-reply-topic")

        override def processRequest(model: RequestModel, replyTopic: String, taskMgr: ContainerTaskManager): Future[Either[String, String]] = mockProcessRecord(model, replyTopic, taskMgr)
      }

      val result = toTest.handleRequest(evt, mock[Context])
      there was one(mockProcessRecord).apply(any,any,any)
    }
  }
}
