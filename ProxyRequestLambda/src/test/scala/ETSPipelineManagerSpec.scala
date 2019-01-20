import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoder
import com.amazonaws.services.elastictranscoder.model._
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import scala.collection.JavaConverters._

class ETSPipelineManagerSpec extends Specification with Mockito {
  "ETSPipelineManager.makeJobRequest" should {
    "send a CreateJob request based on the provided arguments, including custom metadata" in {
      val mockedEtsClient = mock[AmazonElasticTranscoder]
      mockedEtsClient.createJob(any[CreateJobRequest]) returns new CreateJobResult().withJob(new Job().withId("ets-job-id"))

      val mgr = new ETSPipelineManager
      val result = mgr.makeJobRequest("input-path","output-path","preset-id","pipeline-id","job-uuid",ProxyType.UNKNOWN)(mockedEtsClient)

      val expectedJobRequest = new CreateJobRequest()
        .withInput(new JobInput().withKey("input-path"))
          .withOutput(new CreateJobOutput().withKey("output-path").withPresetId("preset-id"))
          .withPipelineId("pipeline-id")
          .withUserMetadata(Map("archivehunter-job-id"->"job-uuid","proxy-type"->"UNKNOWN").asJava)
      there was one(mockedEtsClient).createJob(expectedJobRequest)
      result must beRight("ets-job-id")
    }

    "error with a Left if the create job request fails" in {
      val mockedEtsClient = mock[AmazonElasticTranscoder]
      mockedEtsClient.createJob(any[CreateJobRequest]) throws new RuntimeException("my hovercraft is full of eels")

      val mgr = new ETSPipelineManager
      val result = mgr.makeJobRequest("input-path","output-path","preset-id","pipeline-id","job-uuid",ProxyType.UNKNOWN)(mockedEtsClient)

      val expectedJobRequest = new CreateJobRequest()
        .withInput(new JobInput().withKey("input-path"))
        .withOutput(new CreateJobOutput().withKey("output-path").withPresetId("preset-id"))
        .withPipelineId("pipeline-id")
        .withUserMetadata(Map("archivehunter-job-id"->"job-uuid","proxy-type"->"UNKNOWN").asJava)
      there was one(mockedEtsClient).createJob(expectedJobRequest)
      result must beLeft("java.lang.RuntimeException: my hovercraft is full of eels")
    }
  }
}
