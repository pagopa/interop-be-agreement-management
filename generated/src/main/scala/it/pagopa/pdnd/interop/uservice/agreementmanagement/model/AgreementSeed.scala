package it.pagopa.pdnd.interop.uservice.agreementmanagement.model

import java.util.UUID

/**
 * contains the expected payload for attributes persistence.
 *
 * @param eserviceId  for example: ''null''
 * @param descriptorId  for example: ''null''
 * @param producerId  for example: ''null''
 * @param consumerId  for example: ''null''
 * @param verifiedAttributes set of the verified attributes belonging to this agreement, if any. for example: ''null''
*/
final case class AgreementSeed (
  eserviceId: UUID,
  descriptorId: UUID,
  producerId: UUID,
  consumerId: UUID,
  verifiedAttributes: Seq[VerifiedAttributeSeed]
)


