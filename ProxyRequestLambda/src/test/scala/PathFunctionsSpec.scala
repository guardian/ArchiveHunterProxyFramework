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
}
