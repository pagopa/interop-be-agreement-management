package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence

import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.agreement.{
  PersistentAgreement,
  PersistentVerifiedAttribute
}

@SuppressWarnings(Array("org.wartremover.warts.Equals"))
final case class State(agreements: Map[String, PersistentAgreement]) extends Persistable {
  def add(agreement: PersistentAgreement): State =
    copy(agreements = agreements + (agreement.id.toString -> agreement))

  def getAgreementContainingVerifiedAttribute(agreementId: String, attributeId: String): Option[PersistentAgreement] = {
    for {
      agreement <- agreements.get(agreementId)
      _         <- agreement.verifiedAttributes.find(_.id.toString == attributeId)
    } yield agreement
  }

  def updateAgreementContent(
    agreement: PersistentAgreement,
    updatedAttribute: PersistentVerifiedAttribute
  ): PersistentAgreement = {
    val updatedAttributes = agreement.verifiedAttributes.map(x =>
      x.id.toString match {
        case id if id == updatedAttribute.id.toString => updatedAttribute
        case _                                        => x
      }
    )
    agreement.copy(verifiedAttributes = updatedAttributes)
  }

  def updateAgreement(agreement: PersistentAgreement): State = {
    copy(agreements = agreements + (agreement.id.toString -> agreement))
  }

}

object State {
  val empty: State = State(agreements = Map.empty[String, PersistentAgreement])
}
