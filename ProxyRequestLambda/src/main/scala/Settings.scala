case class Settings(clusterName:String, taskDefinitionName:String, taskContainerName:String,
                    subnets:Option[Seq[String]], replyTopic:String, etsRoleArn:String, etsMessageTopic:String,
                    videoPresetId:String, audioPresetId:String, floodQueue:String, maxRunningTasks:Int) {
  /**
   * extracts the "region" field from the Task Definition Name
   */
  def getTaskRegion = {
    val arnParts = taskDefinitionName.split(":")
    if(arnParts.length<4) {
      None
    } else {
      Some(arnParts(3))
    }
  }

  def updateEnvironmentWithRegion(env:Map[String,String]):Map[String,String] = {
    getTaskRegion match {
      case None=>env
      case Some(rgn)=>env ++ Map("AWS_REGION"->rgn,"AWS_DEFAULT_REGION"->rgn)
    }
  }
}