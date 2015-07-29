import java.util.UUID

import core.{TriggerMetadata, TriggerEvent}
import java.io.{IOException, ByteArrayInputStream}
import java.util.concurrent.TimeoutException
import java.util.zip.ZipInputStream
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import org.specs2.time.NoTimeConversions
import play.api.Application
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
class ForceUtilSpec extends PlaySpecification with NoTimeConversions {

  "ForceUtil" should {

    "login" in new WithApplication {
      val loginResult = await(ForceUtil.login(ForceUtil.ENV_PROD, sys.env("FORCE_USERNAME"), sys.env("FORCE_PASSWORD")))
      (loginResult \ "access_token").asOpt[String] must beSome[String]
    }

    "getSobjects" in new WithApplication {
      val loginResult = await(ForceUtil.login(ForceUtil.ENV_PROD, sys.env("FORCE_USERNAME"), sys.env("FORCE_PASSWORD")))
      val accessToken = (loginResult \ "access_token").as[String]
      val result = await(ForceUtil.getSobjects(ForceUtil.ENV_PROD, accessToken))
      result must not (beNull)
    }

    "createApexClass" in new WithApplication {
      val loginResult = await(ForceUtil.login(ForceUtil.ENV_PROD, sys.env("FORCE_USERNAME"), sys.env("FORCE_PASSWORD")))
      val accessToken = (loginResult \ "access_token").as[String]
      val result = await(ForceUtil.createApexClass(ForceUtil.ENV_PROD, accessToken, "Fooo", "public class Fooo { }"))
      result must not (beNull)
    }

    "createApexTrigger" in new WithApplication {
      val loginResult = await(ForceUtil.login(ForceUtil.ENV_PROD, sys.env("FORCE_USERNAME"), sys.env("FORCE_PASSWORD")))
      val accessToken = (loginResult \ "access_token").as[String]
      val result = await(ForceUtil.createApexTrigger(ForceUtil.ENV_PROD, accessToken, "Barr", "trigger Barr on Account (before insert) { }", "Account"))
      result must not (beNull)
    }

    "getApexTriggers" in new WithApplication {
      val loginResult = await(ForceUtil.login(ForceUtil.ENV_PROD, sys.env("FORCE_USERNAME"), sys.env("FORCE_PASSWORD")))
      val accessToken = (loginResult \ "access_token").as[String]
      val result = await(ForceUtil.getApexTriggers(ForceUtil.ENV_PROD, accessToken))
      result must not (beNull)
    }

    "createRemoteSite" in new WithApplication {
      val loginResult = await(ForceUtil.login(ForceUtil.ENV_PROD, sys.env("FORCE_USERNAME"), sys.env("FORCE_PASSWORD")))
      val accessToken = (loginResult \ "access_token").as[String]
      val result = await(ForceUtil.createRemoteSite(ForceUtil.ENV_PROD, accessToken, "FooSite", "https://foo.com"))
      result must not (beNull)
    }

    // todo: cleanup

  }

}
