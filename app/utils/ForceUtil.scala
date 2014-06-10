package utils

import java.net.URL

import com.sforce.soap.apex.SoapConnection
import com.sforce.soap.metadata._
import com.sforce.ws.{ConnectionException, ConnectorConfig}
import com.sforce.soap.partner.{LoginResult, PartnerConnection}
import core.TriggerEvent.TriggerEvent
import java.io.ByteArrayOutputStream
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.concurrent.{Promise, Future}
import scala.concurrent.duration.{Duration, FiniteDuration}
import play.api.libs.concurrent.Akka
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Play.current
import core.TriggerSource

object ForceUtil {

  val API_VERSION = 29.0

  val LOGIN_URL = "https://login.salesforce.com/services/Soap/u/" + API_VERSION

  def login(username: String, password: String): LoginResult = {
    val loginConfig = new ConnectorConfig()
    loginConfig.setAuthEndpoint(LOGIN_URL)
    loginConfig.setServiceEndpoint(LOGIN_URL)
    loginConfig.setManualLogin(true)
    new PartnerConnection(loginConfig).login(username, password)
  }

  def metadataConnection(sessionId: String, metadataServerUrl: String): MetadataConnection = {
    val metadataConfig = new ConnectorConfig()
    metadataConfig.setServiceEndpoint(metadataServerUrl)
    metadataConfig.setSessionId(sessionId)
    new MetadataConnection(metadataConfig)
  }

  def restUrl(serverUrl: String): String = {
    val url = new URL(serverUrl)
    new URL(url.getProtocol, url.getHost, s"/services/data/v$API_VERSION/").toString
  }

  def createTriggerSource(name: String, sobject: String, events: List[TriggerEvent], url: String): TriggerSource = {
    val packageXml = templates.xml.PackageXml(API_VERSION)
    val trigger = templates.triggers.txt.Webhook(name, sobject, events.map(_.toString), url)
    val triggerMetaXml = templates.triggers.xml.TriggerMeta(API_VERSION)
    val webhook = templates.classes.txt.Webhook()
    val webhookMetaXml = templates.classes.xml.WebhookMeta(API_VERSION)
    val remoteSiteSetting = templates.remoteSiteSettings.xml.RemoteSiteSetting(name, sobject, url)

    TriggerSource(name, packageXml.toString(), trigger.toString(), triggerMetaXml.toString(), webhook.toString(), webhookMetaXml.toString(), remoteSiteSetting.toString())
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

  def deployZip(metadataConnection: MetadataConnection, zip: Array[Byte], timeout: FiniteDuration, pollInterval: FiniteDuration): Future[DeployResult] = {

    try {
      val deployOptions = new DeployOptions()
      deployOptions.setRollbackOnError(true)

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
                else if (deployResult.getDetails.getComponentFailures.length > 0){
                  deployResult.getDetails.getComponentFailures.map(f => f.getFileName + " : " + f.getProblem).mkString(" && ")
                }
                else {
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