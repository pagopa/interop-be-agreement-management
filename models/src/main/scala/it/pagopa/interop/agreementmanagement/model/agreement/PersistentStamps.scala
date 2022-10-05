package it.pagopa.interop.agreementmanagement.model.agreement

final case class PersistentStamps(
  subscription: Option[PersistentStamp],
  activation: Option[PersistentStamp],
  rejection: Option[PersistentStamp],
  suspension: Option[PersistentStamp]
)
