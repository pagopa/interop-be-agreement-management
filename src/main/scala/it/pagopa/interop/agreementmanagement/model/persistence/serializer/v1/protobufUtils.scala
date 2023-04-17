package it.pagopa.interop.agreementmanagement.model.persistence.serializer.v1

import cats.implicits.toTraverseOps
import it.pagopa.interop.agreementmanagement.model.agreement._
import it.pagopa.interop.agreementmanagement.model.persistence.serializer.v1.agreement._
import it.pagopa.interop.commons.utils.TypeConversions.{LongOps, OffsetDateTimeOps, StringOps}
import cats.implicits._
import java.util.UUID
import scala.util.{Failure, Success, Try}

object protobufUtils {

  def toPersistentAgreement(protobufAgreement: AgreementV1): Either[Throwable, PersistentAgreement] = {
    val agreement = for {
      status              <- fromProtobufAgreementState(protobufAgreement.state)
      id                  <- protobufAgreement.id.toUUID
      eserviceId          <- protobufAgreement.eserviceId.toUUID
      descriptorId        <- protobufAgreement.descriptorId.toUUID
      producerId          <- protobufAgreement.producerId.toUUID
      consumerId          <- protobufAgreement.consumerId.toUUID
      createdAt           <- protobufAgreement.createdAt.toOffsetDateTime
      updatedAt           <- protobufAgreement.updatedAt.traverse(_.toOffsetDateTime)
      suspendedAt         <- protobufAgreement.suspendedAt.traverse(_.toOffsetDateTime)
      verifiedAttributes  <- protobufAgreement.verifiedAttributes.traverse(deserializeVerifiedAttribute)
      certifiedAttributes <- protobufAgreement.certifiedAttributes.traverse(deserializeCertifiedAttribute)
      declaredAttributes  <- protobufAgreement.declaredAttributes.traverse(deserializeDeclaredAttribute)
      consumerDocuments   <- protobufAgreement.consumerDocuments.traverse(toPersistentDocument).toTry
      contract            <- protobufAgreement.contract.traverse(toPersistentDocument).toTry
      stamps              <- protobufAgreement.stamps.traverse(toPersistentStamps).toTry
    } yield PersistentAgreement(
      id = id,
      eserviceId = eserviceId,
      descriptorId = descriptorId,
      producerId = producerId,
      consumerId = consumerId,
      state = status,
      verifiedAttributes = verifiedAttributes,
      certifiedAttributes = certifiedAttributes,
      declaredAttributes = declaredAttributes,
      suspendedByConsumer = protobufAgreement.suspendedByConsumer,
      suspendedByProducer = protobufAgreement.suspendedByProducer,
      suspendedByPlatform = protobufAgreement.suspendedByPlatform,
      consumerDocuments = consumerDocuments,
      createdAt = createdAt,
      updatedAt = updatedAt,
      consumerNotes = protobufAgreement.consumerNotes,
      contract = contract,
      stamps = stamps.getOrElse(PersistentStamps()),
      rejectionReason = protobufAgreement.rejectionReason,
      suspendedAt = suspendedAt
    )
    agreement.toEither
  }

  def toPersistentDocument(documentV1: AgreementDocumentV1): Either[Throwable, PersistentAgreementDocument] = {
    val document = for {
      id        <- documentV1.id.toUUID
      createdAt <- documentV1.createdAt.toOffsetDateTime
    } yield PersistentAgreementDocument(
      id = id,
      name = documentV1.name,
      prettyName = documentV1.prettyName,
      contentType = documentV1.contentType,
      path = documentV1.path,
      createdAt = createdAt
    )
    document.toEither
  }

  def toPersistentStamps(stampsV1: StampsV1): Either[Throwable, PersistentStamps] = for {
    submission           <- stampsV1.submission.traverse(toPersistentStamp)
    activation           <- stampsV1.activation.traverse(toPersistentStamp)
    rejection            <- stampsV1.rejection.traverse(toPersistentStamp)
    suspensionByProducer <- stampsV1.suspensionByProducer.traverse(toPersistentStamp)
    suspensionByConsumer <- stampsV1.suspensionByConsumer.traverse(toPersistentStamp)
    upgrade              <- stampsV1.upgrade.traverse(toPersistentStamp)
    archiving            <- stampsV1.archiving.traverse(toPersistentStamp)
  } yield PersistentStamps(
    submission = submission,
    activation = activation,
    rejection = rejection,
    suspensionByProducer = suspensionByProducer,
    suspensionByConsumer = suspensionByConsumer,
    upgrade = upgrade,
    archiving = archiving
  )

  def toPersistentStamp(stampV1: StampV1): Either[Throwable, PersistentStamp] = {
    val stamp: Try[PersistentStamp] = for {
      who  <- stampV1.who.toUUID
      when <- stampV1.when.toOffsetDateTime
    } yield PersistentStamp(who = who, when = when)

    stamp.toEither
  }

  def toProtobufAgreement(persistentAgreement: PersistentAgreement): AgreementV1 =
    AgreementV1(
      id = persistentAgreement.id.toString,
      eserviceId = persistentAgreement.eserviceId.toString,
      descriptorId = persistentAgreement.descriptorId.toString,
      producerId = persistentAgreement.producerId.toString,
      consumerId = persistentAgreement.consumerId.toString,
      state = toProtobufAgreementState(persistentAgreement.state),
      verifiedAttributes = persistentAgreement.verifiedAttributes.map(serializeVerifiedAttribute),
      certifiedAttributes = persistentAgreement.certifiedAttributes.map(serializeCertifiedAttribute),
      declaredAttributes = persistentAgreement.declaredAttributes.map(serializeDeclaredAttribute),
      suspendedByConsumer = persistentAgreement.suspendedByConsumer,
      suspendedByProducer = persistentAgreement.suspendedByProducer,
      suspendedByPlatform = persistentAgreement.suspendedByPlatform,
      consumerDocuments = persistentAgreement.consumerDocuments.map(toProtobufDocument),
      createdAt = persistentAgreement.createdAt.toMillis,
      updatedAt = persistentAgreement.updatedAt.map(_.toMillis),
      consumerNotes = persistentAgreement.consumerNotes,
      contract = persistentAgreement.contract.map(toProtobufDocument),
      stamps = toProtobufStamps(persistentAgreement.stamps).some,
      rejectionReason = persistentAgreement.rejectionReason,
      suspendedAt = persistentAgreement.suspendedAt.map(_.toMillis)
    )

  def toProtobufStamp(stamp: PersistentStamp): StampV1 = StampV1(who = stamp.who.toString, when = stamp.when.toMillis)
  def toProtobufStamps(stamps: PersistentStamps): StampsV1 =
    StampsV1(
      submission = stamps.submission.map(toProtobufStamp),
      activation = stamps.activation.map(toProtobufStamp),
      rejection = stamps.rejection.map(toProtobufStamp),
      suspensionByProducer = stamps.suspensionByProducer.map(toProtobufStamp),
      suspensionByConsumer = stamps.suspensionByConsumer.map(toProtobufStamp),
      upgrade = stamps.upgrade.map(toProtobufStamp),
      archiving = stamps.archiving.map(toProtobufStamp)
    )

  def toProtobufDocument(persistentDocument: PersistentAgreementDocument): AgreementDocumentV1 =
    AgreementDocumentV1(
      id = persistentDocument.id.toString,
      name = persistentDocument.name,
      prettyName = persistentDocument.prettyName,
      contentType = persistentDocument.contentType,
      path = persistentDocument.path,
      createdAt = persistentDocument.createdAt.toMillis
    )

  def serializeVerifiedAttribute(attribute: PersistentVerifiedAttribute): VerifiedAttributeV1 =
    VerifiedAttributeV1.of(id = attribute.id.toString)

  def serializeCertifiedAttribute(attribute: PersistentCertifiedAttribute): CertifiedAttributeV1 =
    CertifiedAttributeV1.of(id = attribute.id.toString)

  def serializeDeclaredAttribute(attribute: PersistentDeclaredAttribute): DeclaredAttributeV1 =
    DeclaredAttributeV1.of(id = attribute.id.toString)

  def deserializeVerifiedAttribute(serialized: VerifiedAttributeV1): Try[PersistentVerifiedAttribute] =
    Try(UUID.fromString(serialized.id)).map(PersistentVerifiedAttribute)

  def deserializeCertifiedAttribute(serialized: CertifiedAttributeV1): Try[PersistentCertifiedAttribute] =
    Try(UUID.fromString(serialized.id)).map(PersistentCertifiedAttribute)

  def deserializeDeclaredAttribute(serialized: DeclaredAttributeV1): Try[PersistentDeclaredAttribute] =
    Try(UUID.fromString(serialized.id)).map(PersistentDeclaredAttribute)

  def toProtobufAgreementState(status: PersistentAgreementState): AgreementStateV1 =
    status match {
      case Draft                      => AgreementStateV1.DRAFT
      case Pending                    => AgreementStateV1.PENDING
      case Active                     => AgreementStateV1.ACTIVE
      case Suspended                  => AgreementStateV1.SUSPENDED
      case Archived                   => AgreementStateV1.ARCHIVED
      case MissingCertifiedAttributes => AgreementStateV1.MISSING_CERTIFIED_ATTRIBUTES
      case Rejected                   => AgreementStateV1.REJECTED
    }

  def fromProtobufAgreementState(status: AgreementStateV1): Try[PersistentAgreementState] =
    status match {
      case AgreementStateV1.DRAFT                        => Success(Draft)
      case AgreementStateV1.PENDING                      => Success(Pending)
      case AgreementStateV1.ACTIVE                       => Success(Active)
      case AgreementStateV1.SUSPENDED                    => Success(Suspended)
      case AgreementStateV1.ARCHIVED                     => Success(Archived)
      case AgreementStateV1.MISSING_CERTIFIED_ATTRIBUTES => Success(MissingCertifiedAttributes)
      case AgreementStateV1.REJECTED                     => Success(Rejected)
      case AgreementStateV1.Unrecognized(value)          =>
        Failure(new RuntimeException(s"Protobuf AgreementStatus deserialization failed. Unrecognized value: $value"))
    }

}
