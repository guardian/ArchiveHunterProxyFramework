import io.circe.{Decoder, Encoder}

//This should be kept in-sync with the corresponding file in the main ArchiveHunter source
object RequestType extends Enumeration {
  type RequestType = Value
  val THUMBNAIL, PROXY, ANALYSE, SETUP_PIPELINE = Value
}

object ProxyType extends Enumeration {
  type ProxyType = Value
  val VIDEO, AUDIO, THUMBNAIL,UNKNOWN = Value
}

trait RequestModelEncoder {
  implicit val requestTypeEncoder = Encoder.enumEncoder(RequestType)
  implicit val requestTypeDecoder = Decoder.enumDecoder(RequestType)

  implicit val proxyTypeEncoder = Encoder.enumEncoder(ProxyType)
  implicit val proxyTypeDecoder = Decoder.enumDecoder(ProxyType)
}