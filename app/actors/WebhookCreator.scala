package actors

import java.util.concurrent.TimeoutException

import akka.actor.Actor
import akka.pattern.pipe
import com.sforce.soap.metadata.{DeployResult, MetadataConnection}
import com.sforce.ws.SoapFaultException
import controllers.Application._
import core.TriggerMetadata
import play.api.mvc.Results.EmptyContent
import utils.ForceUtil

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class WebhookCreator(id: String) extends Actor {

  var createFuture: Future[DeployResult] = Future.failed[DeployResult](new Exception("Process Not Started"))

  override def receive = {
    case CreateWebhook(triggerMetadata, metadataConnection) =>
      val triggerSource = ForceUtil.createTriggerSource(triggerMetadata)

      val zip = ForceUtil.createTriggerZip(triggerSource)

      createFuture = ForceUtil.deployZip(metadataConnection, zip, triggerMetadata, 120.seconds, 1.second)

      sender ! createFuture

    case GetCreateWebhook =>
      sender ! createFuture
  }

}

case class CreateWebhook(triggerMetadata: TriggerMetadata, metadataConnection: MetadataConnection)

case object GetCreateWebhook