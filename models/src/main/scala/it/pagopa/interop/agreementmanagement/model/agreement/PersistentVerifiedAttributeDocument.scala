package it.pagopa.interop.agreementmanagement.model.agreement

import java.time.OffsetDateTime
import java.util.UUID

// TODO Rename to PersistentDocument
final case class PersistentVerifiedAttributeDocument(
  id: UUID,
  name: String,
  contentType: String,
  path: String,
  createdAt: OffsetDateTime
)
