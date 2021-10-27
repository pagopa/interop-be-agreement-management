package it.pagopa.pdnd.interop.uservice.agreementmanagement.model

import java.util.UUID

/**
 * = VerifiedAttributeSeed =
 *
 * represents the details of a verified attribute bound to the agreement.
 *
 * @param id identifier of the attribute as defined on the attribute registry for example: ''null''
 * @param verified flag stating the current verification state of this attribute for example: ''null''
 * @param validityTimespan optional validity timespan, in seconds. for example: ''null''
*/
final case class VerifiedAttributeSeed (
  id: UUID,
  verified: Option[Boolean],
  validityTimespan: Option[Long]
)

