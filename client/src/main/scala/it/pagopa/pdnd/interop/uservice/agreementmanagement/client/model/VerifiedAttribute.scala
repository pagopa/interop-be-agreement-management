/** Agreement Management Micro Service
  * defines the persistence operations for the agreement
  *
  * The version of the OpenAPI document: {{version}}
  * Contact: support@example.com
  *
  * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
  * https://openapi-generator.tech
  * Do not edit the class manually.
  */
package it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model

import java.time.OffsetDateTime
import java.util.UUID

import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.invoker.ApiModel

case class VerifiedAttribute(
  /* identifier of the attribute as defined on the attribute registry */
  id: UUID,
  /* flag stating the current verification state of this attribute */
  verified: Option[Boolean] = None,
  /* timestamp containing the instant of the verification, if any. */
  verificationDate: Option[OffsetDateTime] = None,
  /* optional validity timespan, in seconds. */
  validityTimespan: Option[Long] = None
) extends ApiModel
