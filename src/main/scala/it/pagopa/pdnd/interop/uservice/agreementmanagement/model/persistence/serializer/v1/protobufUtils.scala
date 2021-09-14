package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence.serializer.v1

import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.agreement.{
  PersistentAgreement,
  PersistentAgreementStatus,
  PersistentVerifiedAttribute
}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence.serializer.v1.agreement.{
  AgreementStatusV1,
  AgreementV1,
  VerifiedAttributeV1
}

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, OffsetDateTime, ZoneOffset}
import java.util.UUID
import scala.util.Try

object protobufUtils {

  private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  private def uuidParsing(id: String) = Try { UUID.fromString(id) }.toEither

  def toPersistentAgreement(protobufAgreement: AgreementV1): Either[Throwable, PersistentAgreement] = {
    for {
      status       <- PersistentAgreementStatus.fromText(protobufAgreement.status.name)
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
      status = status,
      verifiedAttributes = protobufAgreement.verifiedAttributes.map(deserializeVerifiedAttribute)
    )
  }

  def toProtobufAgreement(persistentAgreement: PersistentAgreement): Either[Throwable, AgreementV1] = {
    for {
      status <- AgreementStatusV1
        .fromName(persistentAgreement.status.stringify)
        .toRight(new RuntimeException("Protobuf serialization failed"))
    } yield AgreementV1(
      id = persistentAgreement.id.toString,
      eserviceId = persistentAgreement.eserviceId.toString,
      descriptorId = persistentAgreement.descriptorId.toString,
      producerId = persistentAgreement.producerId.toString,
      consumerId = persistentAgreement.consumerId.toString,
      status = status,
      verifiedAttributes = persistentAgreement.verifiedAttributes.map(serializeVerifiedAttribute)
    )
  }

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

}
