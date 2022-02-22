package it.pagopa.interop.agreementmanagement.model.agreement

import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.agreementmanagement.model.{Agreement, AgreementSeed}

import java.time.OffsetDateTime
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
  suspendedByProducer: Option[Boolean],
  createdAt: OffsetDateTime,
  updatedAt: Option[OffsetDateTime]
)

object PersistentAgreement {
  def fromAPI(
    agreement: AgreementSeed,
    uuidSupplier: UUIDSupplier,
    dateTimeSupplier: OffsetDateTimeSupplier
  ): PersistentAgreement =
    PersistentAgreement(
      id = uuidSupplier.get,
      eserviceId = agreement.eserviceId,
      descriptorId = agreement.descriptorId,
      producerId = agreement.producerId,
      consumerId = agreement.consumerId,
      state = PersistentAgreementState.Pending,
      verifiedAttributes = agreement.verifiedAttributes.distinctBy(_.id).map(PersistentVerifiedAttribute.fromAPI),
      suspendedByConsumer = None,
      suspendedByProducer = None,
      createdAt = dateTimeSupplier.get,
      updatedAt = None
    )

  def fromAPIWithActiveState(
    agreement: AgreementSeed,
    uuidSupplier: UUIDSupplier,
    dateTimeSupplier: OffsetDateTimeSupplier
  ): PersistentAgreement =
    PersistentAgreement(
      id = uuidSupplier.get,
      eserviceId = agreement.eserviceId,
      descriptorId = agreement.descriptorId,
      producerId = agreement.producerId,
      consumerId = agreement.consumerId,
      state = PersistentAgreementState.Active,
      verifiedAttributes = agreement.verifiedAttributes.distinctBy(_.id).map(PersistentVerifiedAttribute.fromAPI),
      suspendedByConsumer = None,
      suspendedByProducer = None,
      createdAt = dateTimeSupplier.get,
      updatedAt = None
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
      suspendedByProducer = persistentAgreement.suspendedByProducer,
      createdAt = persistentAgreement.createdAt,
      updatedAt = persistentAgreement.updatedAt
    )
  }
}
