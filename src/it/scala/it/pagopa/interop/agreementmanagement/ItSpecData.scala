package it.pagopa.interop.agreementmanagement

import it.pagopa.interop.agreementmanagement.model.agreement.{
  Pending,
  PersistentAgreement,
  PersistentAgreementDocument,
  PersistentCertifiedAttribute,
  PersistentDeclaredAttribute,
  PersistentStamps,
  PersistentVerifiedAttribute
}

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID

object ItSpecData {
  final val timestamp: OffsetDateTime = OffsetDateTime.of(2022, 12, 31, 11, 22, 33, 0, ZoneOffset.UTC)

  def persistentCertifiedAttribute: PersistentCertifiedAttribute = PersistentCertifiedAttribute(id = UUID.randomUUID())
  def persistentDeclaredAttribute: PersistentDeclaredAttribute   = PersistentDeclaredAttribute(id = UUID.randomUUID())
  def persistentVerifiedAttribute: PersistentVerifiedAttribute   = PersistentVerifiedAttribute(id = UUID.randomUUID())
  def persistentDocument: PersistentAgreementDocument            = PersistentAgreementDocument(
    id = UUID.randomUUID(),
    name = "doc",
    prettyName = "document",
    contentType = "pdf",
    path = "some/where",
    createdAt = timestamp
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
    certifiedAttributes = Seq(persistentCertifiedAttribute),
    declaredAttributes = Seq(persistentDeclaredAttribute),
    verifiedAttributes = Seq(persistentVerifiedAttribute),
    consumerDocuments = Seq(persistentDocument),
    createdAt = timestamp,
    updatedAt = Some(timestamp),
    consumerNotes = None,
    contract = None,
    stamps = PersistentStamps(),
    rejectionReason = None
  )
}
