import java.net.URI
import scala.util.matching.Regex
import java.net.URLDecoder

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
    /*
    if there is a + in the filename, it's decoded to a ' ' and this means that the file can't be found and won't be transcoded.
    So, we encode the + here to make sure that it comes through to the transcoder.
    This is tested in PathFunctionsSpec
     */
    val withReplacement = s3Uri.replaceAll("\\+","%252B")
    val u = new URI(withReplacement)
    if(u.getHost==null){  //if there is no s3:// prefix, then we only get path back. So assume that's the bucket.
      (u.getPath, "")
    } else {
      (u.getHost, stripped(URLDecoder.decode(u.getPath,"UTF-8")))
    }
  }
}
