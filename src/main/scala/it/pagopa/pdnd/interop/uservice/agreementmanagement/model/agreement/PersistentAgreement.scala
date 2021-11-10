package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.agreement

import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.{Agreement, AgreementSeed}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.service.UUIDSupplier

import java.util.UUID

final case class PersistentAgreement(
  id: UUID,
  eserviceId: UUID,
  descriptorId: UUID,
  producerId: UUID,
  consumerId: UUID,
  state: PersistentAgreementState,
  verifiedAttributes: Seq[PersistentVerifiedAttribute],
  suspendedByConsumer: Option[Boolean],
  suspendedByProducer: Option[Boolean]
)

object PersistentAgreement {
  def fromAPI(agreement: AgreementSeed, uuidSupplier: UUIDSupplier): PersistentAgreement =
    PersistentAgreement(
      id = uuidSupplier.get,
      eserviceId = agreement.eserviceId,
      descriptorId = agreement.descriptorId,
      producerId = agreement.producerId,
      consumerId = agreement.consumerId,
      state = PersistentAgreementState.Pending,
      verifiedAttributes = agreement.verifiedAttributes.distinctBy(_.id).map(PersistentVerifiedAttribute.fromAPI),
      suspendedByConsumer = None,
      suspendedByProducer = None
    )

  def fromAPIWithActiveState(agreement: AgreementSeed, uuidSupplier: UUIDSupplier): PersistentAgreement =
    PersistentAgreement(
      id = uuidSupplier.get,
      eserviceId = agreement.eserviceId,
      descriptorId = agreement.descriptorId,
      producerId = agreement.producerId,
      consumerId = agreement.consumerId,
      state = PersistentAgreementState.Active,
      verifiedAttributes = agreement.verifiedAttributes.distinctBy(_.id).map(PersistentVerifiedAttribute.fromAPI),
      suspendedByConsumer = None,
      suspendedByProducer = None
    )

  def toAPI(persistentAgreement: PersistentAgreement): Agreement = {
    Agreement(
      id = persistentAgreement.id,
      eserviceId = persistentAgreement.eserviceId,
      descriptorId = persistentAgreement.descriptorId,
      producerId = persistentAgreement.producerId,
      consumerId = persistentAgreement.consumerId,
      state = persistentAgreement.state.toApi,
      verifiedAttributes = persistentAgreement.verifiedAttributes.map(PersistentVerifiedAttribute.toAPI),
      suspendedByConsumer = persistentAgreement.suspendedByConsumer,
      suspendedByProducer = persistentAgreement.suspendedByProducer
    )
  }
}
