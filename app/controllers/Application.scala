package controllers

import play.api._
import play.api.mvc._
import com.sforce.ws.{SoapFaultException, ConnectionException, ConnectorConfig}
import com.sforce.soap.partner.{LoginResult, PartnerConnection}
import play.api.libs.json.{JsValue, Writes, Json}
import scala.util.{Failure, Success, Try}
import com.sforce.soap.partner.fault.LoginFault
import utils.ForceUtil
import com.sforce.soap.metadata.{AsyncResult, DeployResult, DeployOptions, MetadataConnection}
import scala.concurrent.{Promise, Future}
import core.TriggerEvent
import play.api.http.Status._
import scala.util.Failure
import scala.Some
import play.api.mvc.Result
import scala.util.Success
import scala.concurrent.duration._
import utils.ForceUtil.{TimeoutFuture}
import play.api.libs.concurrent.Akka
import java.util.concurrent.TimeoutException
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Play.current
import play.api.http.Writeable

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
    Ok(views.html.index("Your new application is ready."))
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

  def getWebhooks = Action { request =>



    Ok
  }

  def createWebhook = MetadataConnectionAction.async(parse.json) { request =>

    val triggerSource = ForceUtil.createTriggerSource("Foo", "Opportunity", List(TriggerEvent.BeforeInsert), "http://localhost/foo")

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

  class MetadataConnectionRequest[A](val metadataConnection: MetadataConnection, request: Request[A]) extends WrappedRequest[A](request)

  object MetadataConnectionAction extends ActionBuilder[MetadataConnectionRequest] with ActionRefiner[Request, MetadataConnectionRequest] {
    def refine[A](request: Request[A]): Future[Either[Result, MetadataConnectionRequest[A]]] = Future.successful {

      val maybeMetadataConnection = for {
        sessionId <- request.headers.get("X-SESSION-ID")
        metadataServerUrl <- request.headers.get("X-METADATA-SERVER_URL")
      } yield Try {
        ForceUtil.metadataConnection(sessionId, metadataServerUrl)
      }

      maybeMetadataConnection match {
        case Some(tryMetadataConnection) =>
          tryMetadataConnection match {
            case Success(metadataConnection) =>
              Right(new MetadataConnectionRequest(metadataConnection, request))
            case Failure(error) =>
              Left(InternalServerError(ErrorResponse.fromThrowable(error)))
          }
        case None =>
          Left(BadRequest(ErrorResponse(Error("Missing X-SESSION-ID and/or X-METADATA-SERVER_URL request headers"))))
      }
    }
  }

}