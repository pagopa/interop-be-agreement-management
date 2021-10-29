package it.pagopa.pdnd.interop.uservice.agreementmanagement.model


/**
 * tracks the owner of the change.
 *
 * @param changedBy  for example: ''null''
*/
final case class StatusChangeDetails (
  changedBy: Option[String]
)

