package it.pagopa.interop.agreementmanagement.model.persistence

import it.pagopa.interop.agreementmanagement.model.agreement.PersistentAgreement
import it.pagopa.interop.commons.queue.message.ProjectableEvent

sealed trait Event extends Persistable with ProjectableEvent

final case class AgreementAdded(agreement: PersistentAgreement)           extends Event
final case class AgreementActivated(agreement: PersistentAgreement)       extends Event
final case class AgreementSuspended(agreement: PersistentAgreement)       extends Event
final case class AgreementDeactivated(agreement: PersistentAgreement)     extends Event
final case class VerifiedAttributeUpdated(agreement: PersistentAgreement) extends Event
