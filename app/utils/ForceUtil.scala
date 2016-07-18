package utils

import javax.inject.Inject

import play.api.Configuration
import play.api.http.{HeaderNames, Status}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.mvc.RequestHeader

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.xml.Elem

class ForceUtil @Inject() (configuration: Configuration, ws: WSClient) (implicit ec: ExecutionContext) {

  val API_VERSION = "33.0"

  val consumerKey = configuration.getString("force.oauth.consumer-key").get
  val consumerSecret = configuration.getString("force.oauth.consumer-secret").get

  val ENV_PROD = "prod"
  val ENV_SANDBOX = "sandbox"
  val SALESFORCE_ENV = "salesforce-env"

  def redirectUri(implicit request: RequestHeader): String = {
    controllers.routes.Application.oauthCallback("", "").absoluteURL().replaceAllLiterally("?code=&state=", "")
  }

  def loginUrl(env: String)(implicit request: RequestHeader): String = {
    env match {
      case env @ ENV_PROD => s"https://login.salesforce.com/services/oauth2/authorize?response_type=code&client_id=$consumerKey&redirect_uri=$redirectUri&state=$env"
      case env @ ENV_SANDBOX => s"https://test.salesforce.com/services/oauth2/authorize?response_type=code&client_id=$consumerKey&redirect_uri=$redirectUri&state=$env"
    }
  }

  def login(env: String, username: String, password: String): Future[JsValue] = {
    val body = Map(
      "grant_type" -> "password",
      "client_id" -> consumerKey,
      "client_secret" -> consumerSecret,
      "username" -> username,
      "password" -> password
    ).mapValues(Seq(_))

    ws.url(tokenUrl(env)).post(body).flatMap { response =>
      response.status match {
        case Status.OK => Future.successful(response.json)
        case _ => Future.failed(new Exception(response.body))
      }
    }
  }

  def tokenUrl(env: String): String = env match {
    case ENV_PROD => "https://login.salesforce.com/services/oauth2/token"
    case ENV_SANDBOX => "https://test.salesforce.com/services/oauth2/token"
  }

  def userinfoUrl(env: String): String = env match {
    case ENV_PROD => "https://login.salesforce.com/services/oauth2/userinfo"
    case ENV_SANDBOX => "https://test.salesforce.com/services/oauth2/userinfo"
  }

  def ws(url: String, sessionId: String): WSRequest = {
    ws.url(url).withHeaders(HeaderNames.AUTHORIZATION -> s"Bearer $sessionId")
  }

  def userinfo(env: String, sessionId: String): Future[JsValue] = {
    ws(userinfoUrl(env), sessionId).get().flatMap { response =>
      response.status match {
        case Status.OK => Future.successful(response.json)
        case _ => Future.failed(new Exception(response.body))
      }
    }
  }

  def apiUrl(env: String, sessionId: String, path: String): Future[String] = {
    userinfo(env, sessionId).flatMap { userinfo =>
      (userinfo \ "urls" \ path).asOpt[String].map(_.replaceAllLiterally("{version}", API_VERSION)).fold {
        Future.failed[String](new Exception(s"Could not get the $path URL"))
      } (Future.successful)
    }
  }

  def restUrl(env: String, sessionId: String): Future[String] = {
    apiUrl(env, sessionId, "rest")
  }

  def metadataUrl(env: String, sessionId: String): Future[String] = {
    apiUrl(env, sessionId, "metadata")
  }

  def getSobjects(env: String, sessionId: String): Future[Seq[JsObject]] = {
    restUrl(env, sessionId).flatMap { restUrl =>
      ws(restUrl + "sobjects", sessionId).get().flatMap { response =>
        response.status match {
          case Status.OK => Future.successful((response.json \ "sobjects").as[Seq[JsObject]])
          case _ => Future.failed(new Exception(response.body))
        }
      }
    }
  }

  private def createdResponseToJson(response: WSResponse): Future[JsValue] = {
    def message(json: JsValue): String = (response.json \\ "message").map(_.as[String]).mkString("\n")

    response.status match {
      case Status.CREATED =>
        Future.successful(response.json)

      case Status.BAD_REQUEST if (response.json \\ "errorCode").headOption.flatMap(_.asOpt[String]).contains("DUPLICATE_VALUE") =>
        Future.failed(DuplicateException(message(response.json)))

      case Status.BAD_REQUEST if (response.json \\ "errorCode").nonEmpty =>
        Future.failed(ErrorException(message(response.json)))

      case _ =>
        Future.failed {
          val errorMessage = Try(message(response.json)).getOrElse(response.body)
          new Exception(errorMessage)
        }
    }
  }

  def createApexClass(env: String, sessionId: String, name: String, body: String): Future[JsValue] = {
    restUrl(env, sessionId).flatMap { restUrl =>
      val json = Json.obj(
        "ApiVersion" -> API_VERSION,
        "Body" -> body,
        "Name" -> name
      )
      ws(restUrl + "tooling/sobjects/ApexClass", sessionId).post(json).flatMap(createdResponseToJson)
    }
  }

  def createApexTrigger(env: String, sessionId: String, name: String, body: String, sobject: String): Future[JsValue] = {
    restUrl(env, sessionId).flatMap { restUrl =>
      val json = Json.obj(
        "ApiVersion" -> API_VERSION,
        "Name" -> name,
        "TableEnumOrId" -> sobject,
        "Body" -> body
      )
      ws(restUrl + "tooling/sobjects/ApexTrigger", sessionId).post(json).flatMap(createdResponseToJson)
    }
  }

  def getApexTriggers(env: String, sessionId: String): Future[Seq[JsObject]] = {
    restUrl(env, sessionId).flatMap { restUrl =>
      ws(restUrl + "tooling/query", sessionId).withQueryString("q" -> "SELECT Name, Body from ApexTrigger").get().flatMap { response =>
        response.status match {
          case Status.OK => Future.successful((response.json \ "records").as[Seq[JsObject]])
          case _ => Future.failed(new Exception(response.body))
        }
      }
    }
  }

  // this happens via the SOAP API because it isn't exposed via the REST API
  def createRemoteSite(env: String, sessionId: String, name: String, url: String): Future[Elem] = {
    metadataUrl(env, sessionId).flatMap { metadataUrl =>
      val xml = <env:Envelope xmlns:env="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        <env:Header>
          <urn:SessionHeader xmlns:urn="http://soap.sforce.com/2006/04/metadata">
            <urn:sessionId>{sessionId}</urn:sessionId>
          </urn:SessionHeader>
        </env:Header>
        <env:Body>
          <createMetadata xmlns="http://soap.sforce.com/2006/04/metadata">
            <metadata xsi:type="RemoteSiteSetting">
              <fullName>{name}</fullName>
              <isActive>true</isActive>
              <url>{url}</url>
            </metadata>
          </createMetadata>
        </env:Body>
      </env:Envelope>

      ws(metadataUrl, sessionId).withHeaders("SOAPAction" -> "RemoteSiteSetting", "Content-type" -> "text/xml").post(xml).flatMap { response =>
        response.status match {
          case Status.OK => Future.successful(response.xml)
          case _ => Future.failed(new Exception(response.body))
        }
      }
    }
  }

  case class DuplicateException(message: String) extends Exception {
    override def getMessage = message
  }

  case class ErrorException(message: String) extends Exception {
    override def getMessage = message
  }

}
