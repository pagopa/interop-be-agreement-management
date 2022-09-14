package it.pagopa.interop.agreementmanagement.model

import cats.implicits._
import it.pagopa.interop.agreementmanagement.model.agreement._
import it.pagopa.interop.agreementmanagement.model.persistence.State
import munit.FunSuite

import java.time.OffsetDateTime
import java.util.UUID

class StateSpec extends FunSuite {

  test("State should successfully add verified attribute document") {
    val agreementId1        = UUID.randomUUID()
    val agreementId2        = UUID.randomUUID()
    val existingDocumentId1 = UUID.randomUUID()
    val existingDocumentId2 = UUID.randomUUID()
    val existingDocumentId3 = UUID.randomUUID()
    val newDocumentId       = UUID.randomUUID()
    val newDocument         = persistentDocument(newDocumentId)

    val state        = State(agreements =
      Map(
        agreementId1.toString -> persistentAgreement(agreementId1, Seq(existingDocumentId1, existingDocumentId2)),
        agreementId2.toString -> persistentAgreement(agreementId2, Seq(existingDocumentId3))
      )
    )
    val updatedState = state.addAgreementConsumerDocument(agreementId1.toString, newDocument)

    val oldAgreementConsumerDocs     = state.agreements(agreementId1.toString).consumerDocuments
    val updatedAgreementConsumerDocs = updatedState.agreements(agreementId1.toString).consumerDocuments

    assertEquals(updatedAgreementConsumerDocs.find(_.id == newDocumentId), Some(newDocument))
    assertEquals(updatedAgreementConsumerDocs.filter(_.id != newDocumentId), oldAgreementConsumerDocs)
    assertEquals(state.agreements(agreementId2.toString), updatedState.agreements(agreementId2.toString))
  }

  test("State should not be changed if adding document on non-existing agreement") {
    val agreementId1        = UUID.randomUUID()
    val agreementId2        = UUID.randomUUID()
    val existingDocumentId1 = UUID.randomUUID()
    val existingDocumentId2 = UUID.randomUUID()
    val existingDocumentId3 = UUID.randomUUID()
    val newDocumentId       = UUID.randomUUID()
    val newDocument         = persistentDocument(newDocumentId)

    val state = State(agreements =
      Map(
        agreementId1.toString -> persistentAgreement(agreementId1, Seq(existingDocumentId1, existingDocumentId2)),
        agreementId2.toString -> persistentAgreement(agreementId2, Seq(existingDocumentId3))
      )
    )

    val updatedState = state.addAgreementConsumerDocument("non-existing", newDocument)

    assertEquals(state, updatedState)
  }

  test("State should successfully remove verified attribute document") {
    val agreementId1 = UUID.randomUUID()
    val agreementId2 = UUID.randomUUID()
    val documentId1  = UUID.randomUUID()
    val documentId2  = UUID.randomUUID()
    val documentId3  = UUID.randomUUID()

    val state = State(agreements =
      Map(
        agreementId1.toString -> persistentAgreement(agreementId1, Seq(documentId1, documentId2)),
        agreementId2.toString -> persistentAgreement(agreementId2, Seq(documentId3))
      )
    )

    val updatedState = state.removeAgreementConsumerDocument(agreementId1.toString, documentId1.toString)

    val oldAgreementConsumerDocs     = state.agreements(agreementId1.toString).consumerDocuments
    val updatedAgreementConsumerDocs = updatedState.agreements(agreementId1.toString).consumerDocuments

    assertEquals(updatedAgreementConsumerDocs.find(_.id == documentId1), None)
    assertEquals(oldAgreementConsumerDocs.filter(_.id != documentId1), updatedAgreementConsumerDocs)
    assertEquals(state.agreements(agreementId2.toString), updatedState.agreements(agreementId2.toString))
  }

  test("State should not be changed if removing document on non-existing agreement") {
    val agreementId1 = UUID.randomUUID()
    val agreementId2 = UUID.randomUUID()
    val documentId1  = UUID.randomUUID()
    val documentId2  = UUID.randomUUID()
    val documentId3  = UUID.randomUUID()

    val state = State(agreements =
      Map(
        agreementId1.toString -> persistentAgreement(agreementId1, Seq(documentId1, documentId2)),
        agreementId2.toString -> persistentAgreement(agreementId2, Seq(documentId3))
      )
    )

    val updatedState = state.removeAgreementConsumerDocument("non-existing", documentId1.toString)

    assertEquals(state, updatedState)
  }

  test("State should not be changed if removing document on non-existing document") {
    val agreementId1 = UUID.randomUUID()
    val agreementId2 = UUID.randomUUID()
    val documentId1  = UUID.randomUUID()
    val documentId2  = UUID.randomUUID()
    val documentId3  = UUID.randomUUID()

    val state = State(agreements =
      Map(
        agreementId1.toString -> persistentAgreement(agreementId1, Seq(documentId1, documentId2)),
        agreementId2.toString -> persistentAgreement(agreementId2, Seq(documentId3))
      )
    )

    val updatedState = state.removeAgreementConsumerDocument(agreementId1.toString, "non-existing")

    assertEquals(state, updatedState)
  }

  def persistentDocument(documentId: UUID): PersistentAgreementDocument                   = PersistentAgreementDocument(
    id = documentId,
    name = "doc",
    prettyName = "prettyDoc",
    contentType = "pdf",
    path = "somewhere",
    createdAt = OffsetDateTime.now()
  )
  def persistentAgreement(agreementId: UUID, documentIds: Seq[UUID]): PersistentAgreement = PersistentAgreement(
    id = agreementId,
    eserviceId = UUID.randomUUID(),
    descriptorId = UUID.randomUUID(),
    producerId = UUID.randomUUID(),
    consumerId = UUID.randomUUID(),
    state = Active,
    verifiedAttributes = Nil,
    certifiedAttributes = Nil,
    declaredAttributes = Nil,
    suspendedByConsumer = None,
    suspendedByProducer = None,
    suspendedByPlatform = None,
    consumerDocuments = documentIds.map(persistentDocument),
    createdAt = OffsetDateTime.now(),
    updatedAt = None,
    consumerNotes = "these are consumer notes".some
  )

}
