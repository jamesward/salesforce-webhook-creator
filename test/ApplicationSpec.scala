import core.{TriggerEvent, TriggerMetadata}
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

import play.api.http.MimeTypes
import play.api.libs.Codecs
import play.api.libs.json.Json
import play.api.Logger
import play.api.mvc.Codec
import play.api.test._
import play.api.test.Helpers._
import scala.concurrent.Future
import scala.Some
import scala.Some
import scala.Some

@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {
  /*
  def login() = {
    val request = FakeRequest(POST, controllers.routes.Application.login().url)
      .withJsonBody(Json.obj(
        "username" -> sys.env("FORCE_USERNAME"),
        "password" -> sys.env("FORCE_PASSWORD")
    ))

    route(request).get
  }


  "Application" should {

    "login with valid credentials" in new WithApplication {

      val result = login()

      status(result) must equalTo(OK)
      (contentAsJson(result) \ "metadataServerUrl").as[String] must equalTo(sys.env("FORCE_METADATA_SERVER_URL"))
      (contentAsJson(result) \ "sessionId").asOpt[String] must beSome

    }

    "login with invalid credentials is unauthorized" in new WithApplication {

      val request = FakeRequest(POST, controllers.routes.Application.login().url)
        .withJsonBody(Json.obj(
          "username" -> "something",
          "password" -> "something"
        ))

      val Some(result) = route(request)

      status(result) must equalTo(UNAUTHORIZED)
      (contentAsJson(result) \ "error" \ "code").as[String] must equalTo("INVALID_LOGIN")

    }

    "login without credentials is a bad request" in new WithApplication {

      val request = FakeRequest(POST, controllers.routes.Application.login().url).withJsonBody(Json.obj())

      val Some(result) = route(request)

      status(result) must equalTo(BAD_REQUEST)
      contentType(result) must beSome(MimeTypes.JSON)
    }

    "createWebhook with valid credentials" in new WithApplication {

      val futureLoginResult = login()
      val metadataServerUrl = (contentAsJson(futureLoginResult) \ "metadataServerUrl").as[String]
      val serverUrl = (contentAsJson(futureLoginResult) \ "serverUrl").as[String]
      val sessionId = (contentAsJson(futureLoginResult) \ "sessionId").as[String]

      val request = FakeRequest(POST, controllers.routes.Application.createWebhook().url)
        .withJsonBody(Json.toJson(TriggerMetadata("ContactFoo", "Contact", List(TriggerEvent.BeforeInsert, TriggerEvent.AfterUpdate), "http://localhost/foo")))
        .withHeaders(
          "X-METADATA-SERVER-URL" -> metadataServerUrl,
          "X-SERVER-URL" -> serverUrl,
          "X-SESSION-ID" -> sessionId
        )

      val Some(result) = route(request)

      status(result) must equalTo(OK)

    }

    "createWebhook with invalid credentials is unauthorized" in new WithApplication {
      val request = FakeRequest(POST, controllers.routes.Application.createWebhook().url)
        .withJsonBody(Json.toJson(TriggerMetadata("Foo", "Contact", List(TriggerEvent.BeforeInsert), "http://localhost/foo")))
        .withHeaders(
          "X-SESSION-ID" -> "foo",
          "X-METADATA-SERVER-URL" -> "https://na10.salesforce.com/services/Soap/m/29.0/000000000000000",
          "X-SERVER-URL" -> "https://na10.salesforce.com/services/Soap/u/29.0/000000000000000"
        )

      val Some(result) = route(request)

      status(result) must equalTo(UNAUTHORIZED)
      (contentAsJson(result) \ "error" \ "code").as[String] must equalTo("INVALID_SESSION_ID")
    }

    "createWebhook without credentials is a bad request" in new WithApplication {
      val request = FakeRequest(POST, controllers.routes.Application.createWebhook().url).withJsonBody(Json.obj())

      val Some(result) = route(request)

      status(result) must equalTo(BAD_REQUEST)
      contentType(result) must beSome(MimeTypes.JSON)
    }

    "getWebhooks with valid credentials" in new WithApplication {

      val futureLoginResult = login()
      val metadataServerUrl = (contentAsJson(futureLoginResult) \ "metadataServerUrl").as[String]
      val serverUrl = (contentAsJson(futureLoginResult) \ "serverUrl").as[String]
      val sessionId = (contentAsJson(futureLoginResult) \ "sessionId").as[String]

      val request = FakeRequest(GET, controllers.routes.Application.createWebhook().url)
        .withHeaders(
          "X-METADATA-SERVER-URL" -> metadataServerUrl,
          "X-SERVER-URL" -> serverUrl,
          "X-SESSION-ID" -> sessionId
        )

      val Some(result) = route(request)

      status(result) must equalTo(OK)
      (contentAsJson(result) \\ "name").map(_.as[String]) must contain("ContactFooWebhookTrigger")
      (contentAsJson(result) \\ "sobject").map(_.as[String]) must contain("Contact")
      (contentAsJson(result) \\ "url").map(_.as[String]) must contain("http://localhost/foo")
    }

  }
  */
}
