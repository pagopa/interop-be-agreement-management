package it.pagopa.interop.agreementmanagement.model.persistence

import it.pagopa.interop.agreementmanagement.model.agreement.{PersistentAgreement, PersistentAgreementDocument}
import it.pagopa.interop.commons.queue.message.ProjectableEvent

sealed trait Event extends Persistable with ProjectableEvent

final case class AgreementAdded(agreement: PersistentAgreement)                                     extends Event
final case class AgreementDeleted(agreementId: String)                                              extends Event
final case class AgreementUpdated(agreement: PersistentAgreement)                                   extends Event
final case class AgreementContractAdded(agreementId: String, contract: PersistentAgreementDocument) extends Event
final case class AgreementConsumerDocumentAdded(agreementId: String, document: PersistentAgreementDocument)
    extends Event
final case class AgreementConsumerDocumentRemoved(agreementId: String, documentId: String)          extends Event
final case class VerifiedAttributeUpdated(agreement: PersistentAgreement)                           extends Event

// deprecated
final case class AgreementActivated(agreement: PersistentAgreement)   extends Event
final case class AgreementSuspended(agreement: PersistentAgreement)   extends Event
final case class AgreementDeactivated(agreement: PersistentAgreement) extends Event
