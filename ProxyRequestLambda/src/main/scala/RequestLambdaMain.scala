import com.amazonaws.services.ecs.{AmazonECS, AmazonECSClientBuilder}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.SNSEvent

import scala.collection.JavaConverters._
import io.circe.syntax._
import io.circe.generic.auto._

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import scala.concurrent.duration._

class RequestLambdaMain extends RequestHandler[SNSEvent,Unit] with RequestModelEncoder {
  /**
    * perform the request that we have been asked to
    * @param model RequestModel desribing the request to perform
    * @return a Future containing either the task ARN or a string with an error message
    */
  def processRequest(model: RequestModel, replyTopic:String, taskMgr:ContainerTaskManager):Future[Either[String,String]] = Future {
    model.requestType match {
      case RequestType.THUMBNAIL=>
        taskMgr.runTask(
          command = Seq("/bin/bash","/usr/local/bin/extract_thumbnail.sh", model.inputMediaUri, model.targetLocation, replyTopic),
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
          command = Seq("/usr/bin/python","/usr/local/bin/analyze_media_file.py", model.inputMediaUri, replyTopic),
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
        Left("Creating proxies is not supported through this mechanism yet")
      case _=>
        Left(s"Don't understand requested action ${model.requestType}")
    }
  }

  def getEcsClient = AmazonECSClientBuilder.defaultClient()

  implicit val ecsClient:AmazonECS = getEcsClient

  case class Settings(clusterName:String, taskDefinitionName:String, taskContainerName:String, subnets:Option[Seq[String]], replyTopic:String)

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
    val toWaitFor = Future.sequence(requests.map(rq=>processRequest(rq, settings.replyTopic, taskMgr)))

    val results = Await.result(toWaitFor, 60.seconds)

    val runFailures = results.collect({case Left(err)=>err})
    if(runFailures.nonEmpty){
      println("Got the following errors triggering commands: ")
      runFailures.foreach(err=>println(s"\t$err"))
    }
    println(s"${runFailures.length} out of ${results.length} failed, the rest succeeded")
  }
}
