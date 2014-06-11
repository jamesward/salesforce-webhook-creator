package core

import core.TriggerEvent._
import play.api.libs.json.Json

case class TriggerMetadata(name: String, sobject: String, events: List[TriggerEvent], url: String)

object TriggerMetadata {
  implicit val jsonFormat = Json.format[TriggerMetadata]
}