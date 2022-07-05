package it.pagopa.interop.agreementmanagement.model.agreement

import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import cats.implicits._
import java.time.OffsetDateTime
import java.util.UUID

object PersistentAgreement
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
