import play.api.http.HeaderNames
import play.api.mvc._
import play.filters.gzip.GzipFilter
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

object Global extends WithFilters(new GzipFilter(), OnlyHttpsFilter)

object OnlyHttpsFilter extends Filter {
  def apply(nextFilter: (RequestHeader) => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    nextFilter(requestHeader).map { result =>
      requestHeader.headers.get(HeaderNames.X_FORWARDED_PROTO).filter(_ != "https").fold(result) { proto =>
        Results.MovedPermanently("https://" + requestHeader.host + requestHeader.uri)
      }
    }
  }
}