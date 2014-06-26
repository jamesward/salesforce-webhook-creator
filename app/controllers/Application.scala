package controllers

import core.TriggerEvent
import core.TriggerEvent.TriggerEvent
import play.api._
import play.api.libs.ws.WS
import play.api.mvc._
import com.sforce.ws.SoapFaultException
import play.api.libs.json.{JsValue, Writes, Json}
import scala.util.Try
import com.sforce.soap.partner.fault.LoginFault
import com.sforce.soap.partner.PartnerConnection
import utils.ForceUtil
import com.sforce.soap.metadata.MetadataConnection
import scala.concurrent.Future
import core.{TriggerMetadata, TriggerEvent}
import scala.util.Failure
import play.api.mvc.Result
import scala.util.Success
import scala.concurrent.duration._
import java.util.concurrent.TimeoutException
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.http.Writeable
import scala.collection.JavaConverters._
import play.api.Play.current

object Application extends Controller {

  val consumerKey = Play.current.configuration.getString("force.oauth.consumer-key").get
  val consumerSecret = Play.current.configuration.getString("force.oauth.consumer-secret").get
  val redirectUri = Play.current.configuration.getString("force.oauth.redirect-uri").get

  case class Error(message: String, code: Option[String] = None)
  object Error {
    implicit def jsonFormat = Json.format[Error]
  }

  case class ErrorResponse(error: Error)

  object ErrorResponse {

    def fromThrowable(t: Throwable): ErrorResponse = {
      ErrorResponse(Error(t.getMessage))
    }

    implicit def jsonFormat = Json.format[ErrorResponse]
  }

  implicit def jsWriteable[A](implicit wa: Writes[A], wjs: Writeable[JsValue]): Writeable[A] = wjs.map(Json.toJson(_))


  def index = Action { request =>
    if (request.session.get("oauthAccessToken").isDefined && request.session.get("instanceUrl").isDefined) {
      Redirect(routes.Application.app())
    }
    else {
      Ok(views.html.index(consumerKey, redirectUri))
    }
  }

  def logout = Action {
    Redirect(routes.Application.index()).withNewSession
  }

  def getSobjects = ConnectionAction { request =>
    val sobjects = request.partnerConnection.describeGlobal().getSobjects.map(_.getName)
    Ok(Json.toJson(sobjects))
  }

  def getWebhooks = ConnectionAction { request =>

    val triggers = request.partnerConnection.query("select Name, Body from ApexTrigger").getRecords
    val rawWebhooks = triggers.filter(_.getField("Name").toString.endsWith("WebhookTrigger"))

    // todo: cleanup parsing
    val webhooks = rawWebhooks.map { webhook =>
      val name = webhook.getField("Name").toString
      val body = webhook.getField("Body").toString
      val firstLine = body.lines.next()
      val sobject = firstLine.split(" ")(3)
      val eventsString = firstLine.substring(firstLine.indexOf("(") + 1, firstLine.indexOf(")"))
      val events: List[TriggerEvent] = eventsString.split(",").map(TriggerEvent.withName).toList
      val url = body.substring(body.indexOf("String url = '") + 14, body.indexOf("';"))
      TriggerMetadata(name, sobject, events, url)
    }

    Ok(Json.toJson(webhooks))
  }

  def createWebhook = ConnectionAction.async(parse.json) { request =>

    val maybeTriggerMetadata = request.body.asOpt[TriggerMetadata]

    maybeTriggerMetadata.fold(Future.successful(BadRequest(ErrorResponse(Error("Missing required fields"))))) { triggerMetadata =>
      val triggerSource = ForceUtil.createTriggerSource(triggerMetadata)

      val zip = ForceUtil.createTriggerZip(triggerSource)

      ForceUtil.deployZip(request.metadataConnection, zip, triggerSource.name, 60.seconds, 1.second).map { deployResult =>
        Ok("")
      } recover {
        case e: SoapFaultException if e.getFaultCode.getLocalPart == "INVALID_SESSION_ID" =>
          Unauthorized(ErrorResponse(Error(e.getMessage, Some(e.getFaultCode.getLocalPart))))
        case e: TimeoutException =>
          RequestTimeout(ErrorResponse.fromThrowable(e))
        case e: Exception =>
          InternalServerError(ErrorResponse.fromThrowable(e))
      }
    }
  }

  def app = ConnectionAction {
    Ok(views.html.app())
  }

  def oauthCallback(code: String) = Action.async {
    val url = "https://login.salesforce.com/services/oauth2/token"

    val wsFuture = WS.url(url).withQueryString(
      "grant_type" -> "authorization_code",
      "client_id" -> consumerKey,
      "client_secret" -> consumerSecret,
      "redirect_uri" -> redirectUri,
      "code" -> code
    ).post("")

    wsFuture.map { response =>

      val maybeAppResponse = for {
        accessToken <- (response.json \ "access_token").asOpt[String]
        instanceUrl <- (response.json \ "instance_url").asOpt[String]
      } yield {
        Redirect(routes.Application.app()).withSession("oauthAccessToken" -> accessToken, "instanceUrl" -> instanceUrl)
      }

      maybeAppResponse.getOrElse(Redirect(routes.Application.index()).flashing("error" -> "Could not authenticate"))
    }
  }


  class ConnectionRequest[A](val metadataConnection: MetadataConnection, val partnerConnection:PartnerConnection, request: Request[A]) extends WrappedRequest[A](request)

  object ConnectionAction extends ActionBuilder[ConnectionRequest] with ActionRefiner[Request, ConnectionRequest] {
    def refine[A](request: Request[A]): Future[Either[Result, ConnectionRequest[A]]] = Future.successful {

      val maybeConnections = for {
        sessionId <- request.session.get("oauthAccessToken")
        instanceUrl <- request.session.get("instanceUrl")
      } yield Try {
        val metadataConnection = ForceUtil.metadataConnection(sessionId, instanceUrl + "/services/Soap/m/" + ForceUtil.API_VERSION)
        val partnerConnection = ForceUtil.partnerConnection(sessionId, instanceUrl + "/services/Soap/u/" + ForceUtil.API_VERSION)
        partnerConnection.getUserInfo  // test the connection
        (metadataConnection, partnerConnection)
      }

      maybeConnections match {
        case Some(tryConnections) =>
          tryConnections match {
            case Success(connections) =>
              Right(new ConnectionRequest(connections._1, connections._2, request))
            case Failure(error) =>
              Left(Redirect(routes.Application.index()).withNewSession)
          }
        case None =>
          Left(Redirect(routes.Application.index()).withNewSession)
      }
    }
  }

}
