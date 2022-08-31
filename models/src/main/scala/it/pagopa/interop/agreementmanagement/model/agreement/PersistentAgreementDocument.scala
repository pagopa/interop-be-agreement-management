package it.pagopa.interop.agreementmanagement.model.agreement

import java.time.OffsetDateTime
import java.util.UUID

final case class PersistentAgreementDocument(
  id: UUID,
  name: String,
  prettyName: String,
  contentType: String,
  path: String,
  createdAt: OffsetDateTime
)
