case class RequestModel(requestType: RequestType.Value, inputMediaUri: String,
                        targetLocation:String, jobId:String,
                        createPipelineRequest: Option[CreatePipeline], proxyType:Option[ProxyType.Value])
