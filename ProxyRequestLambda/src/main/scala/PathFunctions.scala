import java.net.URI

object PathFunctions {
  def stripped(str:String,char:String="/") =
    if(str.startsWith(char)){
      str.substring(1)
    } else {
      str
    }
  /**
    * takes the given URI and breaks it into a tuple of (bucket, path)
    * @param s3Uri
    * @return
    */
  def breakdownS3Uri(s3Uri:String) = {
    val u = new URI(s3Uri)
    (u.getHost, stripped(u.getPath))
  }
}
