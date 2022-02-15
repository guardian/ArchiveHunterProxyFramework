import com.amazonaws.services.ecs.model.LaunchType

case class Settings(clusterName:String, taskDefinitionName:String, taskContainerName:String,
                    subnets:Option[Seq[String]], replyTopic:String, etsRoleArn:String, etsMessageTopic:String,
                    videoPresetId:String, audioPresetId:String, floodQueue:String, maxRunningTasks:Int, launchType:Option[LaunchType])
