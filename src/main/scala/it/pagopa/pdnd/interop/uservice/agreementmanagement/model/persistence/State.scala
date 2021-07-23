package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence

import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.{Agreement, VerifiedAttribute}

@SuppressWarnings(Array("org.wartremover.warts.Equals"))
final case class State(agreements: Map[String, Agreement]) extends Persistable {
  def add(agreement: Agreement): State =
    copy(agreements = agreements + (agreement.id.toString -> agreement))

  def getAgreementContainingVerifiedAttribute(agreementId: String, attributeId: String): Option[Agreement] = {
    for {
      agreement <- agreements.get(agreementId)
      _         <- agreement.verifiedAttributes.find(_.id.toString == attributeId)
    } yield agreement
  }

  def updateAgreementContent(agreement: Agreement, updatedAttribute: VerifiedAttribute): Agreement = {
    val updatedAttributes = agreement.verifiedAttributes.map(x =>
      x.id.toString match {
        case id if id == updatedAttribute.id.toString => updatedAttribute
        case _                                        => x
      }
    )
    agreement.copy(verifiedAttributes = updatedAttributes)
  }

  def updateAgreement(agreement: Agreement): State = {
    copy(agreements = agreements + (agreement.id.toString -> agreement))
  }

}

object State {
  val empty: State = State(agreements = Map.empty[String, Agreement])
}
