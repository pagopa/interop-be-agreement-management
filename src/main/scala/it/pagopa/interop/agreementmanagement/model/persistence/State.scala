package it.pagopa.interop.agreementmanagement.model.persistence

import it.pagopa.interop.agreementmanagement.model.agreement.{PersistentAgreement, PersistentAgreementDocument}

final case class State(agreements: Map[String, PersistentAgreement]) extends Persistable {
  def add(agreement: PersistentAgreement): State = copy(agreements = agreements + (agreement.id.toString -> agreement))

  def delete(agreementId: String): State = copy(agreements = agreements - agreementId)

  def updateAgreement(agreement: PersistentAgreement): State =
    copy(agreements = agreements + (agreement.id.toString -> agreement))

  def addAgreementContract(agreementId: String, contract: PersistentAgreementDocument): State = {
    val updatedAgreement = for {
      agreement <- agreements.get(agreementId)
      updatedAgreement = agreement.copy(contract = Some(contract))
    } yield updatedAgreement

    updatedAgreement.fold(this)(agreement => copy(agreements = agreements + (agreementId -> agreement)))
  }

  def addAgreementConsumerDocument(agreementId: String, document: PersistentAgreementDocument): State = {
    val updatedAgreement = for {
      agreement <- agreements.get(agreementId)
      updatedAgreement = agreement.copy(consumerDocuments = agreement.consumerDocuments :+ document)
    } yield updatedAgreement

    updatedAgreement.fold(this)(agreement => copy(agreements = agreements + (agreementId -> agreement)))
  }

  def removeAgreementConsumerDocument(agreementId: String, documentId: String): State = {
    val updatedAgreement = for {
      agreement <- agreements.get(agreementId)
      updatedAgreement = agreement.copy(consumerDocuments =
        agreement.consumerDocuments.filter(_.id.toString != documentId)
      )
    } yield updatedAgreement

    updatedAgreement.fold(this)(agreement => copy(agreements = agreements + (agreementId -> agreement)))
  }
}

object State {
  val empty: State = State(agreements = Map.empty[String, PersistentAgreement])
}
