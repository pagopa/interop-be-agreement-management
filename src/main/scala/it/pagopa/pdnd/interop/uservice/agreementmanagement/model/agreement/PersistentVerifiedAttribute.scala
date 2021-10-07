package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.agreement

import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.{VerifiedAttribute, VerifiedAttributeSeed}

import java.time.OffsetDateTime
import java.util.UUID

final case class PersistentVerifiedAttribute(
  id: UUID,
  verified: Option[Boolean],
  verificationDate: Option[OffsetDateTime],
  validityTimespan: Option[Long]
)

object PersistentVerifiedAttribute {
  def fromAPI(attribute: VerifiedAttributeSeed): PersistentVerifiedAttribute =
    PersistentVerifiedAttribute(
      id = attribute.id,
      verified = attribute.verified,
      verificationDate = attribute.verified match {
        case Some(true) => Some(OffsetDateTime.now())
        case _          => None
      },
      validityTimespan = attribute.validityTimespan
    )
  def toAPI(persistedAttribute: PersistentVerifiedAttribute): VerifiedAttribute =
    VerifiedAttribute(
      id = persistedAttribute.id,
      verified = persistedAttribute.verified,
      verificationDate = persistedAttribute.verificationDate,
      validityTimespan = persistedAttribute.validityTimespan
    )
}
