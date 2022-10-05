package it.pagopa.interop.agreementmanagement.model.agreement

final case class PersistentStamps(
  subscription: Option[PersistentStamp] = None,
  activation: Option[PersistentStamp] = None,
  rejection: Option[PersistentStamp] = None,
  suspension: Option[PersistentStamp] = None,
  upgrade: Option[PersistentStamp] = None,
  archiving: Option[PersistentStamp] = None
)
