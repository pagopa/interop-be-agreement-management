package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence

import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.Agreement

sealed trait Event extends Persistable

final case class AgreementAdded(agreement: Agreement)           extends Event
final case class AgreementActivated(agreement: Agreement)       extends Event
final case class AgreementSuspended(agreement: Agreement)       extends Event
final case class VerifiedAttributeUpdated(agreement: Agreement) extends Event
