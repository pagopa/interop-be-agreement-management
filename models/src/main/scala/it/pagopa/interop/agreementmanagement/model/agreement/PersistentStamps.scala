package it.pagopa.interop.agreementmanagement.model.agreement

final case class PersistentStamps(
  submission: Option[PersistentStamp] = None,
  activation: Option[PersistentStamp] = None,
  rejection: Option[PersistentStamp] = None,
  suspensionByProducer: Option[PersistentStamp] = None,
  suspensionByConsumer: Option[PersistentStamp] = None,
  upgrade: Option[PersistentStamp] = None,
  archiving: Option[PersistentStamp] = None
)
