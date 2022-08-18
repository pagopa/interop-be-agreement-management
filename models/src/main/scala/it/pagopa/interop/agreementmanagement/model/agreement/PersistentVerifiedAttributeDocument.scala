package it.pagopa.interop.agreementmanagement.model.agreement

import java.time.OffsetDateTime
import java.util.UUID

final case class PersistentVerifiedAttributeDocument(
  id: UUID,
  name: String,
  contentType: String,
  path: String,
  createdAt: OffsetDateTime
)
