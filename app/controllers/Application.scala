package controllers

import core.TriggerEvent
import core.TriggerEvent.TriggerEvent
import play.api._
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

object Application extends Controller {

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


  def index = Action {
    Ok(views.html.index())
  }

  def login = Action(parse.json) { request =>

    val maybeLoginResult = for {
      username <- (request.body \ "username").asOpt[String]
      password <- (request.body \ "password").asOpt[String]
    } yield Try {
      ForceUtil.login(username, password)
    }

    maybeLoginResult match {
      case Some(tryLoginResult) =>
        tryLoginResult match {
          case Success(loginResult) =>
            Ok(Json.obj(
              "serverUrl" -> loginResult.getServerUrl,
              "metadataServerUrl" -> loginResult.getMetadataServerUrl,
              "sessionId" -> loginResult.getSessionId
            ))
          case Failure(error: LoginFault) =>
            Unauthorized(ErrorResponse(Error(error.getExceptionMessage, Some(error.getExceptionCode.toString))))
          case Failure(error) =>
            Unauthorized(ErrorResponse.fromThrowable(error))
        }
      case None =>
        BadRequest(ErrorResponse(Error("Username and/or Password not specified")))
    }
  }

  def getSobjects = ConnectionAction { request =>
    val sobjects = request.partnerConnection.describeGlobal().getSobjects.map(_.getName)
    Ok(Json.toJson(sobjects))
  }

  def getWebhooks = ConnectionAction { request =>

    val triggers = request.partnerConnection.query("select Name, Body from ApexTrigger").getRecords
    val rawWebhooks = triggers.filter(_.getField("Name").toString.endsWith("Trigger"))

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

      ForceUtil.deployZip(request.metadataConnection, zip, 60.seconds, 1.second).map { deployResult =>
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

  class ConnectionRequest[A](val metadataConnection: MetadataConnection, val partnerConnection:PartnerConnection, request: Request[A]) extends WrappedRequest[A](request)

  object ConnectionAction extends ActionBuilder[ConnectionRequest] with ActionRefiner[Request, ConnectionRequest] {
    def refine[A](request: Request[A]): Future[Either[Result, ConnectionRequest[A]]] = Future.successful {

      val maybeConnections = for {
        sessionId <- request.headers.get("X-SESSION-ID")
        serverUrl <- request.headers.get("X-SERVER-URL")
        metadataServerUrl <- request.headers.get("X-METADATA-SERVER-URL")
      } yield Try {
        (ForceUtil.metadataConnection(sessionId, metadataServerUrl), ForceUtil.partnerConnection(sessionId, serverUrl))
      }

      maybeConnections match {
        case Some(tryConnections) =>
          tryConnections match {
            case Success(connections) =>
              Right(new ConnectionRequest(connections._1, connections._2, request))
            case Failure(error) =>
              Left(InternalServerError(ErrorResponse.fromThrowable(error)))
          }
        case None =>
          Left(BadRequest(ErrorResponse(Error("Missing X-SESSION-ID, X-SERVER-URL, and/or X-METADATA-SERVER-URL request headers"))))
      }
    }
  }


}
