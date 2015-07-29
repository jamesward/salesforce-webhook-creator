package core

import core.TriggerEvent.TriggerEvent
import play.api.libs.json.Json

case class TriggerMetadata(name: String, sobject: String, events: List[TriggerEvent], url: String)

object TriggerMetadata {
  implicit val format = Json.format[TriggerMetadata]
}