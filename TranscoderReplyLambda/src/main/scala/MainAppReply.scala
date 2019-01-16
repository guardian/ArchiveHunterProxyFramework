import java.util.Base64

case class MainAppReply(status:String, output:Option[String], jobId:String, input:String, log:Option[String])

object MainAppReply extends ((String,Option[String],String,String,Option[String])=>MainAppReply) {
  def withPlainLog(status:String, output:Option[String], jobId:String, input:String, plainLog:Option[String]) = {
    val maybeEncoded = plainLog.map(l=>Base64.getEncoder.encodeToString(l.getBytes))
    new MainAppReply(status,output,jobId,input,maybeEncoded)
  }
}