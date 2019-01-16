import io.circe.{Decoder, Encoder}

object TranscoderState extends Enumeration {
  val PROGRESSING, COMPLETED, WARNING, ERROR = Value
}

trait TranscoderMessageDecoder {
  implicit val transcoderStateEncoder = Encoder.enumEncoder(TranscoderState)
  implicit val transcoderStateDecoder = Decoder.enumDecoder(TranscoderState)
}

case class ETSOutput(id:String, presetId:String, status:String,duration:Option[Long],fileSize:Option[Long],width:Option[Int],height:Option[Int],key:String)

// Amazon Elastic Transcoding message structure
case class AwsElasticTranscodeMsg ( state: TranscoderState.Value,
                                    jobId: String,
                                    pipelineId: String,
                                    outputKeyPrefix:Option[String],
                                    errorCode: Option[Int],
                                    messageDetails: Option[String],
                                    userMetadata:Option[Map[String,String]],
                                    outputs:Option[Seq[ETSOutput]])