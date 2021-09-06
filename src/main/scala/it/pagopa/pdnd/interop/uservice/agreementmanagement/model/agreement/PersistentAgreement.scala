package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.agreement

import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.{Agreement, AgreementSeed}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.service.UUIDSupplier

import java.util.UUID

final case class PersistentAgreement(
  id: UUID,
  eserviceId: UUID,
  producerId: UUID,
  consumerId: UUID,
  status: PersistentAgreementStatus,
  verifiedAttributes: Seq[PersistentVerifiedAttribute]
)

object PersistentAgreement {
  def fromAPI(agreement: AgreementSeed, uuidSupplier: UUIDSupplier): PersistentAgreement =
    PersistentAgreement(
      id = uuidSupplier.get,
      eserviceId = agreement.eserviceId,
      producerId = agreement.producerId,
      consumerId = agreement.consumerId,
      status = PersistentAgreementStatus.Pending,
      verifiedAttributes = agreement.verifiedAttributes.distinctBy(_.id).map(PersistentVerifiedAttribute.fromAPI)
    )

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  def toAPI(persistentAgreement: PersistentAgreement): Agreement = {
    Agreement(
      id = persistentAgreement.id,
      eserviceId = persistentAgreement.eserviceId,
      producerId = persistentAgreement.producerId,
      consumerId = persistentAgreement.consumerId,
      status = persistentAgreement.status.stringify,
      verifiedAttributes = persistentAgreement.verifiedAttributes.map(PersistentVerifiedAttribute.toAPI)
    )
  }
}