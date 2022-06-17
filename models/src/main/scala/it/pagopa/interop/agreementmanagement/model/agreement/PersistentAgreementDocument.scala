package it.pagopa.interop.agreementmanagement.model.agreement

import java.time.OffsetDateTime
import java.util.UUID

final case class PersistentAgreementDocument(id: UUID, contentType: String, path: String, createdAt: OffsetDateTime)
