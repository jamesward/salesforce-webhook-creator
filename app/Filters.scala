import javax.inject.Inject

import akka.stream.Materializer
import play.api.http.{HeaderNames, HttpFilters}
import play.api.mvc.{Filter, RequestHeader, Result, Results}
import play.filters.gzip.GzipFilter

import scala.concurrent.{ExecutionContext, Future}

class OnlyHttpsFilter @Inject() (implicit val mat: Materializer, ec: ExecutionContext) extends Filter {
  def apply(nextFilter: (RequestHeader) => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    nextFilter(requestHeader).map { result =>
      requestHeader.headers.get(HeaderNames.X_FORWARDED_PROTO).filter(_ != "https").fold(result) { proto =>
        Results.MovedPermanently("https://" + requestHeader.host + requestHeader.uri)
      }
    }
  }
}

class Filters @Inject() (gzip: GzipFilter, onlyHttpsFilter: OnlyHttpsFilter) extends HttpFilters {
  val filters = Seq(gzip, onlyHttpsFilter)
}
