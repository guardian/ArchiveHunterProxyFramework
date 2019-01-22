import java.util.Base64


case class MainAppReply(status:JobReportStatus.Value, output:Option[String], jobId:String, input:String, log:Option[String], proxyType:Option[ProxyType.Value], metadata:Option[String])

object MainAppReply extends ((JobReportStatus.Value,Option[String],String,String,Option[String],Option[ProxyType.Value],Option[String])=>MainAppReply) {
  def withPlainLog(status:JobReportStatus.Value, output:Option[String], jobId:String, input:String, plainLog:Option[String], proxyType: Option[ProxyType.Value], metadata:Option[String]) = {
    val maybeEncoded = plainLog.map(l=>Base64.getEncoder.encodeToString(l.getBytes))
    new MainAppReply(status,output,jobId,input,maybeEncoded, proxyType, metadata)
  }
}