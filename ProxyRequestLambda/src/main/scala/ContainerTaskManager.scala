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

    val actualCpu = cpu.getOrElse(1)
    val actualEnvironment = environment.map(entry=>new KeyValuePair().withName(entry._1).withValue(entry._2)).asJavaCollection

    val overrides = new TaskOverride().withContainerOverrides(new ContainerOverride()
      .withCommand(command.asJava)
      .withCpu(actualCpu)
      .withEnvironment(actualEnvironment)
      .withName(taskContainerName)
    )

    //external IP is needed to pull images from Docker Hub
    val netConfig = subnets.map(subnetList=>new NetworkConfiguration().withAwsvpcConfiguration(new AwsVpcConfiguration().withSubnets(subnetList.asJava).withAssignPublicIp(AssignPublicIp.ENABLED)))

    val rq = new RunTaskRequest()
      .withCluster(clusterName)
      .withTaskDefinition(taskDefinitionName)
      .withOverrides(overrides)
      .withLaunchType(launchType.getOrElse(LaunchType.FARGATE))

      val finalRq = netConfig match {
        case Some(config)=>rq.withNetworkConfiguration(config)
        case None=>rq
      }

    val result = client.runTask(finalRq)
    val failures = result.getFailures.asScala
    if(failures.nonEmpty){
      logger.error(s"Failed to launch task: ${failures.head.getArn} ${failures.head.getReason}")
      Failure(new RuntimeException(failures.head.toString))
    } else {
      if(result.getTasks.isEmpty){
        Failure(new RuntimeException("No failures logged but no tasks started"))
      } else {
        Success(result.getTasks.asScala.head)
      }
    }
  }
}
