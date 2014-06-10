package core

object TriggerEvent extends Enumeration {
  type TriggerEvent = Value
  val BeforeInsert = Value("before insert")
  val BeforeUpdate = Value("before update")
  val BeforeDelete = Value("before delete")
  val AfterInsert = Value("after insert")
  val AfterUpdate = Value("after update")
  val AfterDelete = Value("after delete")
  val AfterUndelete = Value("after undelete")
}