package it.pagopa.pdnd.interop.uservice.agreementmanagement.model

import java.util.UUID

/**
 * business representation of an agreement
 *
 * @param id  for example: ''null''
 * @param eserviceId  for example: ''null''
 * @param descriptorId  for example: ''null''
 * @param producerId  for example: ''null''
 * @param consumerId  for example: ''null''
 * @param status  for example: ''null''
 * @param verifiedAttributes set of the verified attributes belonging to this agreement, if any. for example: ''null''
 * @param suspendedByConsumer  for example: ''null''
 * @param suspendedByProducer  for example: ''null''
*/
final case class Agreement (
  id: UUID,
  eserviceId: UUID,
  descriptorId: UUID,
  producerId: UUID,
  consumerId: UUID,
  status: String,
  verifiedAttributes: Seq[VerifiedAttribute],
  suspendedByConsumer: Option[Boolean],
  suspendedByProducer: Option[Boolean]
)

