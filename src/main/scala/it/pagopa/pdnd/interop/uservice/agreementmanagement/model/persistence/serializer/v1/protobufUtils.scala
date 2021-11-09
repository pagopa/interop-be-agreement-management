package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence.serializer.v1

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

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, OffsetDateTime, ZoneOffset}
import java.util.UUID
import scala.util.Try

object protobufUtils {

  private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  private def uuidParsing(id: String): Either[Throwable, UUID] = Try(UUID.fromString(id)).toEither

  def toPersistentAgreement(protobufAgreement: AgreementV1): Either[Throwable, PersistentAgreement] = {
    for {
      status       <- fromProtobufAgreementState(protobufAgreement.state)
      id           <- uuidParsing(protobufAgreement.id)
      eserviceId   <- uuidParsing(protobufAgreement.eserviceId)
      descriptorId <- uuidParsing(protobufAgreement.descriptorId)
      producerId   <- uuidParsing(protobufAgreement.producerId)
      consumerId   <- uuidParsing(protobufAgreement.consumerId)
    } yield PersistentAgreement(
      id = id,
      eserviceId = eserviceId,
      descriptorId = descriptorId,
      producerId = producerId,
      consumerId = consumerId,
      state = status,
      verifiedAttributes = protobufAgreement.verifiedAttributes.map(deserializeVerifiedAttribute),
      suspendedByConsumer = protobufAgreement.suspendedByConsumer,
      suspendedByProducer = protobufAgreement.suspendedByProducer
    )
  }

  def toProtobufAgreement(persistentAgreement: PersistentAgreement): Either[Throwable, AgreementV1] =
    Right(
      AgreementV1(
        id = persistentAgreement.id.toString,
        eserviceId = persistentAgreement.eserviceId.toString,
        descriptorId = persistentAgreement.descriptorId.toString,
        producerId = persistentAgreement.producerId.toString,
        consumerId = persistentAgreement.consumerId.toString,
        state = toProtobufAgreementState(persistentAgreement.state),
        verifiedAttributes = persistentAgreement.verifiedAttributes.map(serializeVerifiedAttribute),
        suspendedByConsumer = persistentAgreement.suspendedByConsumer,
        suspendedByProducer = persistentAgreement.suspendedByProducer
      )
    )

  def serializeVerifiedAttribute(verifiedAttribute: PersistentVerifiedAttribute): VerifiedAttributeV1 = {
    VerifiedAttributeV1.of(
      id = verifiedAttribute.id.toString,
      verified = verifiedAttribute.verified,
      verificationDate = verifiedAttribute.verificationDate.map(fromTime),
      validityTimespan = verifiedAttribute.validityTimespan.map(_.toString)
    )
  }

  def deserializeVerifiedAttribute(serializedVerifiedAttribute: VerifiedAttributeV1): PersistentVerifiedAttribute = {
    PersistentVerifiedAttribute(
      id = UUID.fromString(serializedVerifiedAttribute.id),
      verified = serializedVerifiedAttribute.verified,
      verificationDate = serializedVerifiedAttribute.verificationDate.map(toTime),
      validityTimespan = serializedVerifiedAttribute.validityTimespan.map(_.toLong)
    )
  }

  def fromTime(timestamp: OffsetDateTime): String = timestamp.format(formatter)
  def toTime(timestamp: String): OffsetDateTime = {
    OffsetDateTime.of(LocalDateTime.parse(timestamp, formatter), ZoneOffset.UTC)
  }

  def toProtobufAgreementState(status: PersistentAgreementState): AgreementStateV1 =
    status match {
      case PersistentAgreementState.Pending   => AgreementStateV1.PENDING
      case PersistentAgreementState.Active    => AgreementStateV1.ACTIVE
      case PersistentAgreementState.Suspended => AgreementStateV1.SUSPENDED
      case PersistentAgreementState.Inactive  => AgreementStateV1.INACTIVE
    }
  def fromProtobufAgreementState(status: AgreementStateV1): Either[Throwable, PersistentAgreementState] =
    status match {
      case AgreementStateV1.PENDING   => Right(PersistentAgreementState.Pending)
      case AgreementStateV1.ACTIVE    => Right(PersistentAgreementState.Active)
      case AgreementStateV1.SUSPENDED => Right(PersistentAgreementState.Suspended)
      case AgreementStateV1.INACTIVE  => Right(PersistentAgreementState.Inactive)
      case AgreementStateV1.Unrecognized(value) =>
        Left(new RuntimeException(s"Protobuf AgreementStatus deserialization failed. Unrecognized value: $value"))
    }

}
