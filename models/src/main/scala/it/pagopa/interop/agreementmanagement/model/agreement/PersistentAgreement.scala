package it.pagopa.interop.agreementmanagement.model.agreement

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
  certifiedAttributes: Seq[PersistentCertifiedAttribute],
  declaredAttributes: Seq[PersistentDeclaredAttribute],
  suspendedByConsumer: Option[Boolean],
  suspendedByProducer: Option[Boolean],
  suspendedByPlatform: Option[Boolean],
  consumerDocuments: Seq[PersistentAgreementDocument],
  createdAt: OffsetDateTime,
  updatedAt: Option[OffsetDateTime],
  consumerNotes: Option[String],
  contract: Option[PersistentAgreementDocument],
  stamps: PersistentStamps,
  rejectionReason: Option[String],
  suspendedAt: Option[OffsetDateTime]
)
