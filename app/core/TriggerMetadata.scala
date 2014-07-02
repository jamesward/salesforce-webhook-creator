package core

import core.TriggerEvent._
import play.api.libs.json._

case class TriggerMetadata(name: String, sobject: String, events: List[TriggerEvent], url: String, rollbackOnError: Boolean = true)

object TriggerMetadata {
  // from: http://stackoverflow.com/a/20616678/77409
  implicit val jsonReads = Json.reads[TriggerMetadata].compose {
    __.json.update((__ \ "rollbackOnError").json.copyFrom((__ \ "rollbackOnError").json.pick orElse Reads.pure(Json.toJson(true))))
  }
  implicit val jsonWrites = Json.writes[TriggerMetadata]
}