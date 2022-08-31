package it.pagopa.interop.agreementmanagement

import it.pagopa.interop.agreementmanagement.model.agreement.{Pending, PersistentAgreement, PersistentVerifiedAttribute}

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID

object ItSpecData {
  final val timestamp: OffsetDateTime = OffsetDateTime.of(2022, 12, 31, 11, 22, 33, 0, ZoneOffset.UTC)

  def persistentVerifiedAttribute: PersistentVerifiedAttribute = PersistentVerifiedAttribute(
    id = UUID.randomUUID(),
    verified = Some(true),
    verificationDate = Some(timestamp),
    validityTimespan = Some(1234)
  )

  def persistentAgreement: PersistentAgreement = PersistentAgreement(
    id = UUID.randomUUID(),
    eserviceId = UUID.randomUUID(),
    descriptorId = UUID.randomUUID(),
    consumerId = UUID.randomUUID(),
    producerId = UUID.randomUUID(),
    suspendedByConsumer = Some(false),
    suspendedByProducer = Some(true),
    suspendedByPlatform = Some(false),
    state = Pending,
    verifiedAttributes = Seq(persistentVerifiedAttribute),
    createdAt = timestamp,
    updatedAt = Some(timestamp)
  )
}
