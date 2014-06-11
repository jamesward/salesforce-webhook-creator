package core

import play.api.libs.json._

object TriggerEvent extends Enumeration {
  type TriggerEvent = Value
  val BeforeInsert = Value("before insert")
  val BeforeUpdate = Value("before update")
  val BeforeDelete = Value("before delete")
  val AfterInsert = Value("after insert")
  val AfterUpdate = Value("after update")
  val AfterDelete = Value("after delete")
  val AfterUndelete = Value("after undelete")

  implicit val jsonFormat = new Format[TriggerEvent] {
    def reads(json: JsValue) = JsSuccess(TriggerEvent.withName(json.as[String]))
    def writes(triggerEvent: TriggerEvent) = JsString(triggerEvent.toString)
  }
}