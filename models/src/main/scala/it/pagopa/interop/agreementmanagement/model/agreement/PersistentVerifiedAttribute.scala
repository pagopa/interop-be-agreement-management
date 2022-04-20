package it.pagopa.interop.agreementmanagement.model.agreement

import java.time.OffsetDateTime
import java.util.UUID

object PersistentVerifiedAttribute
final case class PersistentVerifiedAttribute(
  id: UUID,
  verified: Option[Boolean],
  verificationDate: Option[OffsetDateTime],
  validityTimespan: Option[Long]
)
