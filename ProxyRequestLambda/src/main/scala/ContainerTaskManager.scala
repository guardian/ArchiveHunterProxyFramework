import com.amazonaws.services.ecs.AmazonECS
import org.apache.logging.log4j.LogManager
import com.amazonaws.services.ecs.model._

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

class ContainerTaskManager (clusterName:String,taskDefinitionName:String,taskContainerName:String,subnets:Option[Seq[String]]){
  private val logger = LogManager.getLogger(getClass)

  def getPendingTaskCount(implicit client:AmazonECS) = Try {
    val result = client.describeClusters(new DescribeClustersRequest().withClusters(clusterName))
    result.getClusters.asScala.head.getRunningTasksCount + result.getClusters.asScala.head.getPendingTasksCount //we only asked for one cluster, so we should only get one.
  }

  def runTask(command:Seq[String], environment:Map[String,String], name:String, cpu:Option[Int]=None, launchType:Option[LaunchType]=None)(implicit client:AmazonECS) = {
    val launchTypeToUse = launchType.getOrElse(LaunchType.FARGATE)
    val actualEnvironment = environment.map(entry=>new KeyValuePair().withName(entry._1).withValue(entry._2)).asJavaCollection

    val baseOverrides = new TaskOverride().withContainerOverrides(new ContainerOverride()
      .withCommand(command.asJava)
      .withEnvironment(actualEnvironment)
      .withName(taskContainerName)
    )

    val overrides = cpu match {
      case Some(cpu)=> baseOverrides.withCpu(cpu.toString)
      case None=> baseOverrides
    }

    //external IP is needed to pull images from Docker Hub in Fargate
    val netConfig = launchTypeToUse match {
      case LaunchType.FARGATE=> subnets.map(subnetList=>new NetworkConfiguration().withAwsvpcConfiguration(new AwsVpcConfiguration().withSubnets(subnetList.asJava).withAssignPublicIp(AssignPublicIp.ENABLED)))
      case LaunchType.EC2=> subnets.map(subnetList=>new NetworkConfiguration().withAwsvpcConfiguration(new AwsVpcConfiguration().withSubnets(subnetList.asJava)))
    }

    val rq = new RunTaskRequest()
      .withCluster(clusterName)
      .withTaskDefinition(taskDefinitionName)
      .withOverrides(overrides)
      .withLaunchType(launchTypeToUse)

      val finalRq = netConfig match {
        case Some(config)=>rq.withNetworkConfiguration(config)
        case None=>rq
      }

    Try { client.runTask(finalRq) } match {
      case Success(result) =>
        val failures = result.getFailures.asScala
        if (failures.nonEmpty) {
          logger.error(s"Failed to launch task: ${failures.head.getArn} ${failures.head.getReason}")
          Failure(new RuntimeException(failures.head.toString))
        } else {
          if (result.getTasks.isEmpty) {
            Failure(new RuntimeException("No failures logged but no tasks started"))
          } else {
            Success(result.getTasks.asScala.head)
          }
        }
      case Failure(err) =>
        logger.error(s"Failed to launch task: ${err.getMessage}", err)
        Failure(err)
    }
  }
}
