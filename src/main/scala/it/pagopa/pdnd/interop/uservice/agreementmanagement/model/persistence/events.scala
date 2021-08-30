package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence

import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.agreement.PersistentAgreement

sealed trait Event extends Persistable

final case class AgreementAdded(agreement: PersistentAgreement)           extends Event
final case class AgreementActivated(agreement: PersistentAgreement)       extends Event
final case class AgreementSuspended(agreement: PersistentAgreement)       extends Event
final case class VerifiedAttributeUpdated(agreement: PersistentAgreement) extends Event
