case class RequestModel(requestType: RequestType.Value, inputMediaUri: String,
                        targetLocation:String, jobId:String, force:Option[Boolean],
                        createPipelineRequest: Option[CreatePipeline], proxyType:Option[ProxyType.Value]) {
  def hasForce = force.isDefined && force.get
}
