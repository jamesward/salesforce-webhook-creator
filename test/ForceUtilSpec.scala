import play.api.Application
import utils.ForceUtil
import play.api.test._

import scala.util.Random

class ForceUtilSpec extends PlaySpecification {

  def forceUtil (implicit app: Application): ForceUtil = {
    app.injector.instanceOf[ForceUtil]
  }

  lazy val name = Random.alphanumeric.take(8).mkString

  "ForceUtil" should {

    "login" in new WithApplication {
      val loginResult = await(forceUtil.login(forceUtil.ENV_PROD, sys.env("FORCE_USERNAME"), sys.env("FORCE_PASSWORD")))
      (loginResult \ "access_token").asOpt[String] must beSome[String]
    }

    "getSobjects" in new WithApplication {
      val loginResult = await(forceUtil.login(forceUtil.ENV_PROD, sys.env("FORCE_USERNAME"), sys.env("FORCE_PASSWORD")))
      val accessToken = (loginResult \ "access_token").as[String]
      val result = await(forceUtil.getSobjects(forceUtil.ENV_PROD, accessToken))
      result must not (beNull)
    }

    "createApexClass" in new WithApplication {
      val loginResult = await(forceUtil.login(forceUtil.ENV_PROD, sys.env("FORCE_USERNAME"), sys.env("FORCE_PASSWORD")))
      val accessToken = (loginResult \ "access_token").as[String]
      val result = await(forceUtil.createApexClass(forceUtil.ENV_PROD, accessToken, s"Fooo$name", s"public class Fooo$name { }"))
      result must not (beNull)
    }

    "createApexTrigger" in new WithApplication {
      val loginResult = await(forceUtil.login(forceUtil.ENV_PROD, sys.env("FORCE_USERNAME"), sys.env("FORCE_PASSWORD")))
      val accessToken = (loginResult \ "access_token").as[String]
      val result = await(forceUtil.createApexTrigger(forceUtil.ENV_PROD, accessToken, "Barr", s"trigger Barr$name on Account (before insert) { }", "Account"))
      result must not (beNull)
    }

    "getApexTriggers" in new WithApplication {
      val loginResult = await(forceUtil.login(forceUtil.ENV_PROD, sys.env("FORCE_USERNAME"), sys.env("FORCE_PASSWORD")))
      val accessToken = (loginResult \ "access_token").as[String]
      val result = await(forceUtil.getApexTriggers(forceUtil.ENV_PROD, accessToken))
      result must not (beNull)
    }

    "createRemoteSite" in new WithApplication {
      val loginResult = await(forceUtil.login(forceUtil.ENV_PROD, sys.env("FORCE_USERNAME"), sys.env("FORCE_PASSWORD")))
      val accessToken = (loginResult \ "access_token").as[String]
      val result = await(forceUtil.createRemoteSite(forceUtil.ENV_PROD, accessToken, s"FooSite$name", "https://foo.com"))
      result must not (beNull)
    }

    // todo: cleanup

  }

}
