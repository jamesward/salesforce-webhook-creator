import core.{TriggerEvent, TriggerMetadata}
import play.api.Application
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification, WithApplication}
import utils.ForceUtil

import scala.util.Random

class ApplicationSpec extends PlaySpecification {

  def forceUtil(implicit app: Application): ForceUtil = {
    app.injector.instanceOf[ForceUtil]
  }

  def accessToken(implicit app: Application): String = {
    val loginResult = await(forceUtil(app).login(forceUtil(app).ENV_PROD, sys.env("FORCE_USERNAME"), sys.env("FORCE_PASSWORD")))
    (loginResult \ "access_token").as[String]
  }

  lazy val name = Random.alphanumeric.take(8).mkString

  "Application" should {

    "createWebhook with valid credentials" in new WithApplication {

      val request = FakeRequest(POST, controllers.routes.Application.createWebhook.url)
        .withJsonBody(Json.toJson(TriggerMetadata(s"Contact$name", "Contact", List(TriggerEvent.BeforeInsert, TriggerEvent.AfterUpdate), "http://localhost/foo")))
        .withSession(
          "oauthAccessToken" -> accessToken,
          "env" -> forceUtil.ENV_PROD
        )

      val Some(result) = route(app, request)

      status(result) must equalTo(OK)
    }

    "createWebhook with invalid credentials is a bad request" in new WithApplication {
      val request = FakeRequest(POST, controllers.routes.Application.createWebhook.url)
        .withJsonBody(Json.toJson(TriggerMetadata("Foo", "Contact", List(TriggerEvent.BeforeInsert), "http://localhost/foo")))
        .withSession(
          "oauthAccessToken" -> "FOO",
          "env" -> forceUtil.ENV_PROD
        )

      val Some(result) = route(app, request)

      status(result) must equalTo(BAD_REQUEST)
      (contentAsJson(result) \ "error" \ "message").as[String] must equalTo("Bad_OAuth_Token")
    }

    "createWebhook without credentials is a redirect" in new WithApplication {
      val request = FakeRequest(POST, controllers.routes.Application.createWebhook.url).withJsonBody(Json.obj())

      val Some(result) = route(app, request)

      status(result) must equalTo(SEE_OTHER)
    }

    "getWebhooks with valid credentials" in new WithApplication {

      val requestWithSession = FakeRequest(GET, controllers.routes.Application.getWebhooks.url)
        .withSession(
          "oauthAccessToken" -> accessToken,
          "env" -> forceUtil.ENV_PROD
        )

      val Some(resultWithSession) = route(app, requestWithSession)

      status(resultWithSession) must equalTo(OK)
      (contentAsJson(resultWithSession) \\ "name").map(_.as[String]) must contain(s"Contact${name}WebhookTrigger")
      (contentAsJson(resultWithSession) \\ "sobject").map(_.as[String]) must contain("Contact")
      (contentAsJson(resultWithSession) \\ "url").map(_.as[String]) must contain("http://localhost/foo")

      val requestWithHeaders = FakeRequest(GET, controllers.routes.Application.getWebhooks.url)
        .withHeaders(HeaderNames.AUTHORIZATION -> s"Bearer $accessToken", "Env" -> forceUtil.ENV_PROD)

      val Some(resultWithHeaders) = route(app, requestWithHeaders)

      status(resultWithHeaders) must equalTo(OK)
    }
  }

}
