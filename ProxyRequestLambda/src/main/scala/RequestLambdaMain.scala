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

class RequestLambdaMain extends RequestHandler[SNSEvent,Unit]{
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
          command = Seq("/bin/bash","/usr/local/bin/analyze_media_file.py", model.inputMediaUri, model.targetLocation, replyTopic),
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
    }
  }

  implicit val ecsClient:AmazonECS = AmazonECSClientBuilder.defaultClient()

  override def handleRequest(evt: SNSEvent, context: Context): Unit = {
    val maybeReqeustsList = evt.getRecords.asScala.toSeq.map(rec=>{
      io.circe.parser.parse(rec.getSNS.getMessage).flatMap(_.as[RequestModel])
    })

    val failures = maybeReqeustsList.collect({case Left(err)=>err})

    if(failures.nonEmpty){
      failures.foreach(err=>{
        println(s"Could not decode request: ${err.toString}")
      })
    }

    val clusterName = sys.env.get("CLUSTER_NAME") match {
      case Some(str)=>str
      case None=>throw new RuntimeException("You need to specify CLUSTER_NAME")
    }

    val taskDefinitionName = sys.env.get("TASK_DEFINITION") match {
      case Some(str)=>str
      case None=>throw new RuntimeException("You need to specify TASK_DEFINITION")
    }

    val taskContainerName = sys.env.get("TASK_CONTAINER") match {
      case Some(str)=>str
      case None=>throw new RuntimeException("You need to specify TASK_CONTAINER")
    }

    val subnets = sys.env.get("SUBNET_LIST") match {
      case Some(list)=>list.split(",")
      case None=>throw new RuntimeException("You need to specify SUBNET_LIST")
    }

    val replyTopic = sys.env.get("REPLY_TOPIC_ARN") match {
      case Some(str)=>str
      case None=>throw new RuntimeException("You need to specify REPLY_TOPIC_ARN")
    }


    val taskMgr = new ContainerTaskManager(clusterName,taskDefinitionName,taskContainerName, subnets)
    val requests = maybeReqeustsList.collect({case Right(rq)=>rq})
    val toWaitFor = Future.sequence(requests.map(rq=>processRequest(rq, replyTopic, taskMgr)))

    val results = Await.result(toWaitFor, 60.seconds)

    val runFailures = results.collect({case Left(err)=>err})
    if(runFailures.nonEmpty){
      println("Got the following errors triggering commands: ")
      runFailures.foreach(err=>println(s"\t$err"))
    }
    println(s"${runFailures.length} out of ${results.length} failed, the rest succeeded")
  }
}
