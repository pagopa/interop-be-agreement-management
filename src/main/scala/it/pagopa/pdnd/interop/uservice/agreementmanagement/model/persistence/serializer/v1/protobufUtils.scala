package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence.serializer.v1

import cats.implicits.toTraverseOps
import it.pagopa.pdnd.interop.commons.utils.TypeConversions.{LongOps, OffsetDateTimeOps, StringOps}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.agreement.{
  PersistentAgreement,
  PersistentAgreementState,
  PersistentVerifiedAttribute
}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence.serializer.v1.agreement.{
  AgreementStateV1,
  AgreementV1,
  VerifiedAttributeV1
}

import java.util.UUID
import scala.util.{Failure, Success, Try}

object protobufUtils {

  def toPersistentAgreement(protobufAgreement: AgreementV1): Either[Throwable, PersistentAgreement] = {
    val agreement = for {
      status       <- fromProtobufAgreementState(protobufAgreement.state)
      id           <- protobufAgreement.id.toUUID
      eserviceId   <- protobufAgreement.eserviceId.toUUID
      descriptorId <- protobufAgreement.descriptorId.toUUID
      producerId   <- protobufAgreement.producerId.toUUID
      consumerId   <- protobufAgreement.consumerId.toUUID
      createdAt    <- protobufAgreement.createdAt.toOffsetDateTime
      updatedAt    <- protobufAgreement.updatedAt.traverse(_.toOffsetDateTime)
      attributes   <- protobufAgreement.verifiedAttributes.traverse(deserializeVerifiedAttribute)
    } yield PersistentAgreement(
      id = id,
      eserviceId = eserviceId,
      descriptorId = descriptorId,
      producerId = producerId,
      consumerId = consumerId,
      state = status,
      verifiedAttributes = attributes,
      suspendedByConsumer = protobufAgreement.suspendedByConsumer,
      suspendedByProducer = protobufAgreement.suspendedByProducer,
      createdAt = createdAt,
      updatedAt = updatedAt
    )
    agreement.toEither
  }

  def toProtobufAgreement(persistentAgreement: PersistentAgreement): Either[Throwable, AgreementV1] = {
    val protobufEntity = for {
      attributes <- persistentAgreement.verifiedAttributes.traverse(serializeVerifiedAttribute)
    } yield AgreementV1(
      id = persistentAgreement.id.toString,
      eserviceId = persistentAgreement.eserviceId.toString,
      descriptorId = persistentAgreement.descriptorId.toString,
      producerId = persistentAgreement.producerId.toString,
      consumerId = persistentAgreement.consumerId.toString,
      state = toProtobufAgreementState(persistentAgreement.state),
      verifiedAttributes = attributes,
      suspendedByConsumer = persistentAgreement.suspendedByConsumer,
      suspendedByProducer = persistentAgreement.suspendedByProducer,
      createdAt = persistentAgreement.createdAt.toMillis,
      updatedAt = persistentAgreement.updatedAt.map(_.toMillis)
    )

    protobufEntity.toEither
  }

  def serializeVerifiedAttribute(verifiedAttribute: PersistentVerifiedAttribute): Try[VerifiedAttributeV1] = {
    for {
      verificationDate <- verifiedAttribute.verificationDate.traverse(t => t.asFormattedString)
    } yield VerifiedAttributeV1.of(
      id = verifiedAttribute.id.toString,
      verified = verifiedAttribute.verified,
      verificationDate = verificationDate,
      validityTimespan = verifiedAttribute.validityTimespan.map(_.toString)
    )
  }

  def deserializeVerifiedAttribute(
    serializedVerifiedAttribute: VerifiedAttributeV1
  ): Try[PersistentVerifiedAttribute] = {
    for {
      verificationDate <- serializedVerifiedAttribute.verificationDate.traverse(s => s.toOffsetDateTime)
    } yield PersistentVerifiedAttribute(
      id = UUID.fromString(serializedVerifiedAttribute.id),
      verified = serializedVerifiedAttribute.verified,
      verificationDate = verificationDate,
      validityTimespan = serializedVerifiedAttribute.validityTimespan.map(_.toLong)
    )
  }

  def toProtobufAgreementState(status: PersistentAgreementState): AgreementStateV1 =
    status match {
      case PersistentAgreementState.Pending   => AgreementStateV1.PENDING
      case PersistentAgreementState.Active    => AgreementStateV1.ACTIVE
      case PersistentAgreementState.Suspended => AgreementStateV1.SUSPENDED
      case PersistentAgreementState.Inactive  => AgreementStateV1.INACTIVE
    }
  def fromProtobufAgreementState(status: AgreementStateV1): Try[PersistentAgreementState] =
    status match {
      case AgreementStateV1.PENDING   => Success(PersistentAgreementState.Pending)
      case AgreementStateV1.ACTIVE    => Success(PersistentAgreementState.Active)
      case AgreementStateV1.SUSPENDED => Success(PersistentAgreementState.Suspended)
      case AgreementStateV1.INACTIVE  => Success(PersistentAgreementState.Inactive)
      case AgreementStateV1.Unrecognized(value) =>
        Failure(new RuntimeException(s"Protobuf AgreementStatus deserialization failed. Unrecognized value: $value"))
    }

}
