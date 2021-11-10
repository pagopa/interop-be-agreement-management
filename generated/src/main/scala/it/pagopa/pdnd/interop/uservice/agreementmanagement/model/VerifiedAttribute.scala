package it.pagopa.pdnd.interop.uservice.agreementmanagement.model

import java.time.OffsetDateTime
import java.util.UUID

/**
 * = VerifiedAttribute =
 *
 * represents the details of a verified attribute bound to the agreement.
 *
 * @param id identifier of the attribute as defined on the attribute registry for example: ''null''
 * @param verified flag stating the current verification state of this attribute for example: ''null''
 * @param verificationDate timestamp containing the instant of the verification, if any. for example: ''null''
 * @param validityTimespan optional validity timespan, in seconds. for example: ''null''
*/
final case class VerifiedAttribute (
  id: UUID,
  verified: Option[Boolean] = None,
  verificationDate: Option[OffsetDateTime] = None,
  validityTimespan: Option[Long] = None
)


