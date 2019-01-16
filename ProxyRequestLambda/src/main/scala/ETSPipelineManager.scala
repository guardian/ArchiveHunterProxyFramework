import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoder
import com.amazonaws.services.elastictranscoder.model._
import org.apache.logging.log4j.LogManager

import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._

object ETSPipelineManager {
  private val logger = LogManager.getLogger(getClass)

  /**
    * checks existing pipelines in the account to try to find one that goes from the selected input to the selected
    * output bucket
    * @param inputBucket name of the required source bucket
    * @param outputBucket name of the required destination bucket
    * @return a Sequence containing zero or more pipelines. If no pipelines are found, the sequence is empty.
    */
  protected def findPipelineFor(inputBucket:String, outputBucket:String)(implicit etsClient:AmazonElasticTranscoder) = {
    def getNextPage(matches:Seq[Pipeline], pageToken: Option[String]):Seq[Pipeline] = {
      val rq = new ListPipelinesRequest()
      val updatedRq = pageToken match {
        case None=>rq
        case Some(token)=>rq.withPageToken(token)
      }

      val result = etsClient.listPipelines(updatedRq).getPipelines.asScala
      logger.debug(s"findPipelineFor: checking in $result")
      if(result.isEmpty){
        logger.debug(s"findPipelineFor: returning $matches")
        matches
      } else {
        val newMatches = result.filter(p=>p.getOutputBucket==outputBucket && p.getInputBucket==inputBucket)
        logger.debug(s"findPipelineFor: got $newMatches to add")
        matches ++ newMatches
      }
    }

    Try {
      val initialResult = getNextPage(Seq(), None)
      logger.debug(s"findPipelineFor: initial result is $initialResult")
      val finalResult = initialResult.filter(p => p.getName.contains("archivehunter")) //filter out anything that is not ours
      logger.debug(s"findPipelineFor: final result is $finalResult")
      finalResult
    }
  }

  protected def getPipelineStatus(pipelineId:String)(implicit etsClient:AmazonElasticTranscoder) = Try {
    val rq = new ReadPipelineRequest().withId(pipelineId)

    val result = etsClient.readPipeline(rq)
    result.getPipeline.getStatus
  }

  /**
    * wait until the pipeline is in an Active state
    * @param pipelineId pipeline ID to wait on
    * @param etsClient implicitly provided ETS client
    */
  def waitForCompletion(pipelineId:String)(implicit etsClient:AmazonElasticTranscoder):Try[String] = {
    while(true){
       getPipelineStatus(pipelineId) match {
         case Success(status)=>
           println(s"Status of pipeline $pipelineId is $status")
           if (status.toLowerCase == "active") {
            return Success(pipelineId)
           } else if(status.toLowerCase=="error") {
            return Failure(new RuntimeException("Could not create pipeline"))
           }
         case Failure(err)=>
           println(s"ERROR: Could not get pipeline status: $err")
       }
      Thread.sleep(1000)
    }
    Failure(new RuntimeException("Code shouldn't reach here"))
  }

  /**
    * kick of the creation of a pipeline. NOTE: the Pipeline object returned will not be usable until it's in an active state.
    * @param pipelineName name of the pipeline to create
    * @param inputBucket input bucket it should point to
    * @param outputBucket output bucket it should point to
    * @return
    */
  def createEtsPipeline(rq:CreatePipelineRequest, transcodingRole:String)(implicit etsClient:AmazonElasticTranscoder) = Try {
      val result = etsClient.createPipeline(rq)
      val warnings = result.getWarnings.asScala
      if(warnings.nonEmpty){
        logger.warn("Warnings were receieved when creating pipeline:")
        warnings.foreach(warning=>logger.warn(warning.toString))
      }
      result.getPipeline
    }

}
