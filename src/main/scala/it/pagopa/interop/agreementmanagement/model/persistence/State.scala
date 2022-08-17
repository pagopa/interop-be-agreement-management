package it.pagopa.interop.agreementmanagement.model.persistence

import it.pagopa.interop.agreementmanagement.model.agreement.{PersistentAgreement, PersistentVerifiedAttributeDocument}

final case class State(agreements: Map[String, PersistentAgreement]) extends Persistable {
  def add(agreement: PersistentAgreement): State = copy(agreements = agreements + (agreement.id.toString -> agreement))

  def updateAgreement(agreement: PersistentAgreement): State =
    copy(agreements = agreements + (agreement.id.toString -> agreement))

  // TODO Test me
  def addAttributeDocument(
    agreementId: String,
    attributeId: String,
    document: PersistentVerifiedAttributeDocument
  ): State = {
    val updatedAgreement = for {
      agreement <- agreements.get(agreementId)
      attribute <- agreement.verifiedAttributes.find(_.id.toString == attributeId)
      updatedAttribute = attribute.copy(documents = attribute.documents :+ document)
      updatedAgreement = agreement.copy(verifiedAttributes =
        agreement.verifiedAttributes.filter(_.id.toString != attributeId) :+ updatedAttribute
      )
    } yield updatedAgreement

    updatedAgreement.fold(this)(agreement => copy(agreements = agreements + (agreementId -> agreement)))
  }

  // TODO Test me
  def removeAttributeDocument(agreementId: String, attributeId: String, documentId: String): State = {
    val updatedAgreement = for {
      agreement <- agreements.get(agreementId)
      attribute <- agreement.verifiedAttributes.find(_.id.toString == attributeId)
      updatedAttribute = attribute.copy(documents = attribute.documents.filter(_.id.toString != documentId))
      updatedAgreement = agreement.copy(verifiedAttributes =
        agreement.verifiedAttributes.filter(_.id.toString != attributeId) :+ updatedAttribute
      )
    } yield updatedAgreement

    updatedAgreement.fold(this)(agreement => copy(agreements = agreements + (agreementId -> agreement)))
  }
}

object State {
  val empty: State = State(agreements = Map.empty[String, PersistentAgreement])
}
