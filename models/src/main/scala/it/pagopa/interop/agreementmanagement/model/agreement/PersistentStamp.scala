package it.pagopa.interop.agreementmanagement.model.agreement

import java.time.OffsetDateTime
import java.util.UUID

final case class PersistentStamp(who: UUID, when: OffsetDateTime)
