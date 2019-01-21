import org.specs2.mutable.Specification

class PathFunctionsSpec extends Specification {
  "PathFunctions.removeExtension" should {
    "return the filename part with no extension" in {
      val result = PathFunctions.removeExtension("/path/to/file.ext")
      result must beSome("/path/to/file")
    }

    "not blow up if there is no extension on the file" in {
      val result = PathFunctions.removeExtension("/path/to/file")
      result must beNone
    }
  }

  "PathFunctions.breakdownS3Uri" should {
    "split an S3 URI" in {
      val result = PathFunctions.breakdownS3Uri("s3://bucket/path/to/file.ext")
      result mustEqual ("bucket","path/to/file.ext")
    }

    "not blow up if there is no path part to the URI" in {
      val result = PathFunctions.breakdownS3Uri("s3://bucket")
      result mustEqual("bucket","")
    }

    "hmm" in {
      val result = PathFunctions.breakdownS3Uri("bucket-name")
      result mustEqual("bucket-name","")
    }
  }
}
