package utils

import java.net.URL

import com.sforce.soap.apex.SoapConnection
import com.sforce.soap.metadata._
import com.sforce.ws.{ConnectionException, ConnectorConfig}
import com.sforce.soap.partner.{LoginResult, PartnerConnection}
import core.TriggerEvent.TriggerEvent
import java.io.ByteArrayOutputStream
import java.util.zip.{ZipEntry, ZipOutputStream}
import play.api.Play
import play.api.mvc.RequestHeader

import scala.concurrent.{Promise, Future}
import scala.concurrent.duration.{Duration, FiniteDuration}
import play.api.libs.concurrent.Akka
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Play.current
import core.{TriggerMetadata, TriggerSource}

object ForceUtil {

  val API_VERSION = 29.0

  val consumerKey = Play.current.configuration.getString("force.oauth.consumer-key").get
  val consumerSecret = Play.current.configuration.getString("force.oauth.consumer-secret").get
  val redirectUri = Play.current.configuration.getString("force.oauth.redirect-uri").get

  val ENV_PROD = "prod"
  val ENV_SANDBOX = "sandbox"
  val SALESFORCE_ENV = "salesforce-env"

  def loginUrl(env: String)(implicit request: RequestHeader): String = env match {
    case e @ ENV_PROD => "https://login.salesforce.com/services/oauth2/authorize?response_type=code&client_id=%s&redirect_uri=%s&state=%s".format(consumerKey, redirectUri, e)
    case e @ ENV_SANDBOX => "https://test.salesforce.com/services/oauth2/authorize?response_type=code&client_id=%s&redirect_uri=%s&state=%s".format(consumerKey, redirectUri, e)
  }

  def tokenUrl(env: String): String = env match {
    case ENV_PROD => "https://login.salesforce.com/services/oauth2/token"
    case ENV_SANDBOX => "https://test.salesforce.com/services/oauth2/token"
  }

  def userinfoUrl(env: String): String = env match {
    case ENV_PROD => "https://login.salesforce.com/services/oauth2/userinfo"
    case ENV_SANDBOX => "https://test.salesforce.com/services/oauth2/userinfo"
  }

  def metadataConnection(sessionId: String, metadataServerUrl: String): MetadataConnection = {
    val metadataConfig = new ConnectorConfig()
    metadataConfig.setServiceEndpoint(metadataServerUrl)
    metadataConfig.setSessionId(sessionId)
    new MetadataConnection(metadataConfig)
  }

  def partnerConnection(sessionId: String, serverUrl: String): PartnerConnection = {
    val connectorConfig = new ConnectorConfig()
    connectorConfig.setServiceEndpoint(serverUrl)
    connectorConfig.setSessionId(sessionId)
    new PartnerConnection(connectorConfig)
  }

  def createTriggerSource(triggerMetadata: TriggerMetadata): TriggerSource = {
    val packageXml = templates.xml.PackageXml(API_VERSION)
    val trigger = templates.triggers.txt.Webhook(triggerMetadata.name, triggerMetadata.sobject, triggerMetadata.events.map(_.toString), triggerMetadata.url)
    val triggerMetaXml = templates.triggers.xml.TriggerMeta(API_VERSION)
    val webhook = templates.classes.txt.Webhook()
    val webhookMetaXml = templates.classes.xml.WebhookMeta(API_VERSION)
    val triggerTest = templates.classes.txt.TriggerTest(triggerMetadata.name, triggerMetadata.sobject, triggerMetadata.events.map(_.toString), triggerMetadata.url)
    val triggerTestMetaXml = templates.classes.xml.TriggerTestMeta(API_VERSION)
    val remoteSiteSetting = templates.remoteSiteSettings.xml.RemoteSiteSetting(triggerMetadata.name, triggerMetadata.sobject, triggerMetadata.url)

    TriggerSource(triggerMetadata.name, packageXml.toString(), trigger.toString(), triggerMetaXml.toString(), webhook.toString(), webhookMetaXml.toString(), triggerTest.toString(), triggerTestMetaXml.toString(), remoteSiteSetting.toString())
  }

  def createTriggerZip(triggerSource: TriggerSource): Array[Byte] = {
    val byteArrayOutputStream = new ByteArrayOutputStream()
    val zipOutputStream = new ZipOutputStream(byteArrayOutputStream)

    zipOutputStream.putNextEntry(new ZipEntry("unpackaged/"))
    zipOutputStream.closeEntry()

    val packageXml = new ZipEntry("unpackaged/package.xml")
    zipOutputStream.putNextEntry(packageXml)
    zipOutputStream.write(triggerSource.packageXml.getBytes)
    zipOutputStream.closeEntry()

    zipOutputStream.putNextEntry(new ZipEntry("unpackaged/triggers/"))
    zipOutputStream.closeEntry()

    val trigger = new ZipEntry(s"unpackaged/triggers/${triggerSource.name}WebhookTrigger.trigger")
    zipOutputStream.putNextEntry(trigger)
    zipOutputStream.write(triggerSource.trigger.getBytes)
    zipOutputStream.closeEntry()

    val triggerMetaXml = new ZipEntry(s"unpackaged/triggers/${triggerSource.name}WebhookTrigger.trigger-meta.xml")
    zipOutputStream.putNextEntry(triggerMetaXml)
    zipOutputStream.write(triggerSource.triggerMetaXml.getBytes)
    zipOutputStream.closeEntry()

    zipOutputStream.putNextEntry(new ZipEntry("unpackaged/classes/"))
    zipOutputStream.closeEntry()

    val webhook = new ZipEntry(s"unpackaged/classes/Webhook.cls")
    zipOutputStream.putNextEntry(webhook)
    zipOutputStream.write(triggerSource.webhook.getBytes)
    zipOutputStream.closeEntry()

    val webhookMetaXml = new ZipEntry(s"unpackaged/classes/Webhook.cls-meta.xml")
    zipOutputStream.putNextEntry(webhookMetaXml)
    zipOutputStream.write(triggerSource.webhookMetaXml.getBytes)
    zipOutputStream.closeEntry()

    val triggerTest = new ZipEntry(s"unpackaged/classes/${triggerSource.name}WebhookTriggerTest.cls")
    zipOutputStream.putNextEntry(triggerTest)
    zipOutputStream.write(triggerSource.triggerTest.getBytes)
    zipOutputStream.closeEntry()

    val triggerTestMeta = new ZipEntry(s"unpackaged/classes/${triggerSource.name}WebhookTriggerTest.cls-meta.xml")
    zipOutputStream.putNextEntry(triggerTestMeta)
    zipOutputStream.write(triggerSource.triggerTestMeta.getBytes)
    zipOutputStream.closeEntry()

    zipOutputStream.putNextEntry(new ZipEntry("unpackaged/settings/"))
    zipOutputStream.closeEntry()

    val remoteSiteSetting = new ZipEntry(s"unpackaged/remoteSiteSettings/${triggerSource.name}WebhookRemoteSite.remoteSite")
    zipOutputStream.putNextEntry(remoteSiteSetting)
    zipOutputStream.write(triggerSource.remoteSiteSetting.getBytes)
    zipOutputStream.closeEntry()

    zipOutputStream.finish()

    val bytes = byteArrayOutputStream.toByteArray

    zipOutputStream.close()
    byteArrayOutputStream.close()

    bytes
  }

  def deployZip(metadataConnection: MetadataConnection, zip: Array[Byte], triggerMetadata: TriggerMetadata, timeout: FiniteDuration, pollInterval: FiniteDuration): Future[DeployResult] = {

    try {
      val deployOptions = new DeployOptions()

      // this results in code coverage errors because not all of the tests run
      //deployOptions.setRunTests(Array(triggerMetadata.name + "WebhookTriggerTest"))

      deployOptions.setRunAllTests(true)
      deployOptions.setRollbackOnError(triggerMetadata.rollbackOnError)
      deployOptions.setPerformRetrieve(false)
      deployOptions.setIgnoreWarnings(true)

      val asyncResult = metadataConnection.deploy(zip, deployOptions)

      TimeoutFuture(timeout) {
        val promise = Promise[DeployResult]()
        val polling = Akka.system.scheduler.schedule(Duration.Zero, pollInterval) {
          try {
            val deployResult = metadataConnection.checkDeployStatus(asyncResult.getId, true)
            if (deployResult.isDone) {
              if (deployResult.getStatus == DeployStatus.Succeeded) {
                promise.trySuccess(deployResult)
              }
              else {
                val message = if (deployResult.getErrorMessage != null) {
                  deployResult.getErrorMessage
                }
                else if (deployResult.getDetails.getComponentFailures.length > 0) {
                  deployResult.getDetails.getComponentFailures.map(f => f.getFileName + " : " + f.getProblem).mkString(" && ")
                }
                else if (deployResult.getDetails.getRunTestResult.getFailures.length > 0) {
                  deployResult.getDetails.getRunTestResult.getFailures.map(f => f.getMessage).mkString(" && ")
                }
                else if (deployResult.getDetails.getRunTestResult.getCodeCoverageWarnings.length > 0) {
                  deployResult.getDetails.getRunTestResult.getCodeCoverageWarnings.map(f => f.getMessage).mkString(" && ")
                }
                else {
                  println(deployResult)
                  "Unknown Error"
                }

                promise.tryFailure(new Exception(message))
              }
            }
          }
          catch {
            case e: ConnectionException =>
              promise.tryFailure(e)
          }
        }
        promise.future.onComplete(result => polling.cancel())
        promise.future
      }
    }
    catch {
      case e: Exception =>
        Future.failed(e)
    }
  }

  // From: http://stackoverflow.com/questions/16304471/scala-futures-built-in-timeout
  object TimeoutFuture {
    def apply[A](timeout: FiniteDuration)(future: Future[A]): Future[A] = {

      val promise = Promise[A]()

      Akka.system.scheduler.scheduleOnce(timeout) {
        promise.tryFailure(new java.util.concurrent.TimeoutException)
      }

      promise.completeWith(future)

      promise.future
    }
  }

}