import java.util

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.sns.{AmazonSNS, AmazonSNSClientBuilder}
import com.amazonaws.services.sns.model.{MessageAttributeValue, PublishRequest}
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClientBuilder}
import com.amazonaws.services.sqs.model.ReceiveMessageRequest

import scala.collection.JavaConverters._

class SweeperLambdaMain extends RequestHandler[java.util.LinkedHashMap[String,Object],Unit]{
  protected def getSqsQueue = sys.env("FLOOD_QUEUE_URL")
  protected def getSnsTopic = sys.env("INPUT_TOPIC_ARN")
  protected def messageLimit = sys.env.get("MESSAGE_LIMIT").map(_.toInt)

  protected def getSqsClient = AmazonSQSClientBuilder.defaultClient()
  protected def getSnsClient = AmazonSNSClientBuilder.defaultClient()

  /**
    * pull messages from an SQS queue and republish them.
    *
    * @param queueUrl
    * @param topicArn
    * @param client
    * @param snsClient
    * @return true if there are potentially more to go, false if we stop now.
    */
  def pullMessages(queueUrl:String, topicArn:String, client:AmazonSQS, snsClient:AmazonSNS) = {
    val baseRq = new ReceiveMessageRequest().withQueueUrl(queueUrl).withWaitTimeSeconds(2)
    val rq = messageLimit match {
      case None=>baseRq
        //SQS will receive a maximum of 10 messages in a batch
      case Some(limit)=>if(limit<10) baseRq.withMaxNumberOfMessages(limit) else baseRq.withMaxNumberOfMessages(10)
    }

    val result = client.receiveMessage(rq)
    val msgs = result.getMessages.asScala.toList

    println(s"Received ${msgs.length} messages")
    if(msgs.isEmpty){
      0
    } else {
      msgs.foreach(msg=>{
        val attribs = msg.getAttributes.asScala
        val attempt = attribs.get("archivehunter_attempt").map(_.toInt).getOrElse(0)

        val sendRq = new PublishRequest()
          .withTopicArn(topicArn)
          .withMessage(msg.getBody)
          .withMessageAttributes(Map("archivehunter_attempt"->new MessageAttributeValue().withStringValue(attempt.toString).withDataType("Number")).asJava)

        val result = snsClient.publish(sendRq)
        println(s"Re-sent message with id ${result.getMessageId}")
      })
      msgs.length
    }
  }

  override def handleRequest(i: util.LinkedHashMap[String, Object], context: Context): Unit = {
    println("Sweeper lambda starting up")

    var ctr=0
    var received = 0
    do {
      println("Pulling more messages")
      received = pullMessages(getSqsQueue, getSnsTopic, getSqsClient, getSnsClient)
      ctr += received
      println(s"Processed message count: $ctr")
    } while (received>0)

    println("Sweeper lambda completed")
  }
}
