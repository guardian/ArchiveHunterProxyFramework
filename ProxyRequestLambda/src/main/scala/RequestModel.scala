import io.circe.{Decoder, Encoder}

//This should be kept in-sync with the corresponding file in the main ArchiveHunter source
object RequestType extends Enumeration {
  type RequestType = Value
  val THUMBNAIL, PROXY, ANALYSE,SETUP_PIPELINE = Value
}

case class RequestModel (requestType: RequestType.Value, inputMediaUri: String, targetLocation:String, jobId:String, createPipelineRequest: Option[CreatePipeline])

trait RequestModelEncoder {
  implicit val requestTypeEncoder = Encoder.enumEncoder(RequestType)
  implicit val requestTypeDecoder = Decoder.enumDecoder(RequestType)
}