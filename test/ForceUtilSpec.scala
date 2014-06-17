import java.util.UUID

import com.sforce.soap.metadata.{DeployStatus, DeployResult, MetadataConnection}
import com.sforce.soap.partner.sobject.SObject
import core.{TriggerMetadata, TriggerEvent}
import java.io.{IOException, ByteArrayInputStream}
import java.util.concurrent.TimeoutException
import java.util.zip.ZipInputStream
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import org.specs2.time.NoTimeConversions
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.libs.ws.WS
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Failure
import utils.ForceUtil
import play.api.test._
import play.api.test.Helpers._

@RunWith(classOf[JUnitRunner])
class ForceUtilSpec extends Specification with NoTimeConversions {

  /*
  val testTriggerMetadata = TriggerMetadata("Foo", "Opportunity", List(TriggerEvent.BeforeInsert), "http://localhost/foo")

  "ForceUtil" should {

    "login" in {
      val loginResult = ForceUtil.login(sys.env("FORCE_USERNAME"), sys.env("FORCE_PASSWORD"))

      loginResult.getSessionId must not(beNull[String])
    }

    "metadataConnection" in {
      val loginResult = ForceUtil.login(sys.env("FORCE_USERNAME"), sys.env("FORCE_PASSWORD"))
      val metadataConnection = ForceUtil.metadataConnection(loginResult.getSessionId, loginResult.getMetadataServerUrl)
      metadataConnection must not(beNull[MetadataConnection])
    }

    "createTriggerSource" in {

      val triggerSource = ForceUtil.createTriggerSource(testTriggerMetadata)

      triggerSource.packageXml must contain(
        """    <types>
          |        <members>*</members>
          |        <name>ApexTrigger</name>
          |    </types>""".stripMargin)

      triggerSource.trigger must contain("""trigger FooWebhookTrigger on Opportunity (before insert) {""")

      triggerSource.triggerMetaXml must beEqualTo(
      """<?xml version="1.0" encoding="UTF-8"?>
        |<ApexTrigger xmlns="http://soap.sforce.com/2006/04/metadata">
        |    <apiVersion>29.0</apiVersion>
        |    <status>Active</status>
        |</ApexTrigger>""".stripMargin)
    }

    "createTriggerZip" in {

      val triggerSource = ForceUtil.createTriggerSource(testTriggerMetadata)

      val triggerZip = ForceUtil.createTriggerZip(triggerSource)

      triggerZip.length must beGreaterThan(0)

      val byteArrayInputStream = new ByteArrayInputStream(triggerZip)
      val zipInputStream = new ZipInputStream(byteArrayInputStream)

      val unpackagedDir = zipInputStream.getNextEntry
      unpackagedDir.getName must beEqualTo("unpackaged/")
      unpackagedDir.isDirectory must beTrue

      val packageXml = zipInputStream.getNextEntry
      packageXml.getName must beEqualTo("unpackaged/package.xml")

      val triggersDir = zipInputStream.getNextEntry
      triggersDir.getName must beEqualTo("unpackaged/triggers/")
      triggersDir.isDirectory must beTrue

      val trigger = zipInputStream.getNextEntry
      trigger.getName must beEqualTo(s"unpackaged/triggers/${triggerSource.name}WebhookTrigger.trigger")

      val triggerMetaXml = zipInputStream.getNextEntry
      triggerMetaXml.getName must beEqualTo(s"unpackaged/triggers/${triggerSource.name}WebhookTrigger.trigger-meta.xml")

      zipInputStream.close() must not(throwAn[IOException])
      byteArrayInputStream.close() must not(throwAn[IOException])
    }

    "deployZip must work" in new WithApplication {

      val loginResult = ForceUtil.login(sys.env("FORCE_USERNAME"), sys.env("FORCE_PASSWORD"))

      val metadataConnection = ForceUtil.metadataConnection(loginResult.getSessionId, loginResult.getMetadataServerUrl)

      val triggerSource = ForceUtil.createTriggerSource(testTriggerMetadata)

      val triggerZip = ForceUtil.createTriggerZip(triggerSource)

      val futureDeployResult = ForceUtil.deployZip(metadataConnection, triggerZip, 60.seconds, 1.second)

      futureDeployResult must beAnInstanceOf[DeployResult].await(timeout = 60.seconds)
    }

    "deployZip must fail with a short timeout" in new WithApplication {

      val loginResult = ForceUtil.login(sys.env("FORCE_USERNAME"), sys.env("FORCE_PASSWORD"))

      val metadataConnection = ForceUtil.metadataConnection(loginResult.getSessionId, loginResult.getMetadataServerUrl)

      val triggerSource = ForceUtil.createTriggerSource(testTriggerMetadata)

      val triggerZip = ForceUtil.createTriggerZip(triggerSource)

      val futureDeployResult = ForceUtil.deployZip(metadataConnection, triggerZip, 1.second, 1.second)

      Await.result(futureDeployResult, 60.seconds) must throwA[TimeoutException]
    }

    "deployZip must fail with an invalid sobject" in new WithApplication {

      val loginResult = ForceUtil.login(sys.env("FORCE_USERNAME"), sys.env("FORCE_PASSWORD"))

      val metadataConnection = ForceUtil.metadataConnection(loginResult.getSessionId, loginResult.getMetadataServerUrl)

      val triggerSource = ForceUtil.createTriggerSource(testTriggerMetadata.copy(sobject = "Bar"))

      val triggerZip = ForceUtil.createTriggerZip(triggerSource)

      val futureDeployResult = ForceUtil.deployZip(metadataConnection, triggerZip, 60.seconds, 1.second)

      Await.result(futureDeployResult, 60.seconds) must throwA[Exception]("Invalid SObject type name: Bar")
    }

    "create webhook and use it" in new WithApplication {

      val webhookUrl = "http://echo-webhook.herokuapp.com/_test-" + UUID.randomUUID().toString

      val loginResult = ForceUtil.login(sys.env("FORCE_USERNAME"), sys.env("FORCE_PASSWORD"))

      val metadataConnection = ForceUtil.metadataConnection(loginResult.getSessionId, loginResult.getMetadataServerUrl)

      val triggerSource = ForceUtil.createTriggerSource(TriggerMetadata("Account", "Account", List(TriggerEvent.BeforeInsert), webhookUrl))

      val triggerZip = ForceUtil.createTriggerZip(triggerSource)

      val futureDeployResult = ForceUtil.deployZip(metadataConnection, triggerZip, 60.seconds, 1.second)

      Await.result(futureDeployResult, 60.seconds).getStatus must beEqualTo(DeployStatus.Succeeded)

      val account = new SObject()
      account.setType("Account")
      account.setField("Name", "Test")

      val saveResult = ForceUtil.partnerConnection(loginResult.getSessionId, loginResult.getServerUrl).create(Array(account)).head

      saveResult.getSuccess must beTrue

      // give it a few
      Thread.sleep(5000)

      val checkWebhookResponse = Await.result(WS.url(webhookUrl).get(), 30.seconds)

      checkWebhookResponse.status must beEqualTo(OK)
      checkWebhookResponse.body must contain(""""Name":"Test"""")
    }

  }
  */
}
