package it.pagopa.interop.agreementmanagement.model.agreement

import java.util.UUID

final case class PersistentVerifiedAttribute(id: UUID, documents: Seq[PersistentVerifiedAttributeDocument])
