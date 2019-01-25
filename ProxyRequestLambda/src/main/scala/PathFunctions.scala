import java.net.URI
import scala.util.matching.Regex

object PathFunctions {
  val removeXtnRegex:Regex = "^(.*)\\.([^\\.]+)$".r

  def stripped(str:String,char:String="/") =
    if(str.startsWith(char)){
      str.substring(1)
    } else {
      str
    }

  def removeExtension(path:String) = {
    try {
      val removeXtnRegex(file, xtn) = path
      Some(file)
    } catch {
      case ex:scala.MatchError=>
        None
    }
  }

  /**
    * takes the given URI and breaks it into a tuple of (bucket, path)
    * @param s3Uri
    * @return
    */
  def breakdownS3Uri(s3Uri:String) = {
    val u = new URI(s3Uri)
    if(u.getHost==null){  //if there is no s3:// prefix, then we only get path back. So assume that';s the bucket.
      (u.getPath, "")
    } else {
      (u.getHost, stripped(u.getPath))
    }
  }
}
