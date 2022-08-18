package it.pagopa.interop.agreementmanagement.model

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

    val state               = State(agreements =
      Map(
        agreementId1.toString -> persistentAgreement(agreementId1, Seq(existingDocumentId1, existingDocumentId2)),
        agreementId2.toString -> persistentAgreement(agreementId2, Seq(existingDocumentId3))
      )
    )
    val attributeIdToUpdate = state.agreements(agreementId1.toString).verifiedAttributes.head.id.toString

    val updatedState = state.addAttributeDocument(agreementId1.toString, attributeIdToUpdate, newDocument)

    val oldAttribute     =
      state.agreements(agreementId1.toString).verifiedAttributes.find(_.id.toString == attributeIdToUpdate).get
    val updatedAttribute =
      updatedState.agreements(agreementId1.toString).verifiedAttributes.find(_.id.toString == attributeIdToUpdate).get

    assertEquals(updatedAttribute.documents.find(_.id == newDocumentId), Some(newDocument))
    assertEquals(updatedAttribute.documents.filter(_.id != newDocumentId), oldAttribute.documents)
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

    val attributeIdToUpdate = state.agreements(agreementId1.toString).verifiedAttributes.head.id.toString
    val updatedState        = state.addAttributeDocument("non-existing", attributeIdToUpdate, newDocument)

    assertEquals(state, updatedState)
  }

  test("State should not be changed if adding document on non-existing attribute") {
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

    val updatedState = state.addAttributeDocument(agreementId1.toString, "non-existing", newDocument)

    assertEquals(state, updatedState)
  }

  test("State should successfully remove verified attribute document") {
    val agreementId1 = UUID.randomUUID()
    val agreementId2 = UUID.randomUUID()
    val documentId1  = UUID.randomUUID()
    val documentId2  = UUID.randomUUID()
    val documentId3  = UUID.randomUUID()

    val state               = State(agreements =
      Map(
        agreementId1.toString -> persistentAgreement(agreementId1, Seq(documentId1, documentId2)),
        agreementId2.toString -> persistentAgreement(agreementId2, Seq(documentId3))
      )
    )
    val attributeIdToUpdate = state.agreements(agreementId1.toString).verifiedAttributes.head.id.toString

    val updatedState = state.removeAttributeDocument(agreementId1.toString, attributeIdToUpdate, documentId1.toString)

    val oldAttribute     =
      state.agreements(agreementId1.toString).verifiedAttributes.find(_.id.toString == attributeIdToUpdate).get
    val updatedAttribute =
      updatedState.agreements(agreementId1.toString).verifiedAttributes.find(_.id.toString == attributeIdToUpdate).get

    assertEquals(updatedAttribute.documents.find(_.id == documentId1), None)
    assertEquals(oldAttribute.documents.filter(_.id != documentId1), updatedAttribute.documents)
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

    val attributeIdToUpdate = state.agreements(agreementId1.toString).verifiedAttributes.head.id.toString
    val updatedState        = state.removeAttributeDocument("non-existing", attributeIdToUpdate, documentId1.toString)

    assertEquals(state, updatedState)
  }

  test("State should not be changed if removing document on non-existing attribute") {
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

    val updatedState = state.removeAttributeDocument(agreementId1.toString, "non-existing", documentId1.toString)

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

    val attributeIdToUpdate = state.agreements(agreementId1.toString).verifiedAttributes.head.id.toString
    val updatedState        = state.removeAttributeDocument(agreementId1.toString, attributeIdToUpdate, "non-existing")

    assertEquals(state, updatedState)
  }

  def persistentDocument(documentId: UUID): PersistentVerifiedAttributeDocument = PersistentVerifiedAttributeDocument(
    id = documentId,
    name = "doc",
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
    verifiedAttributes =
      Seq(PersistentVerifiedAttribute(id = UUID.randomUUID(), documents = documentIds.map(persistentDocument))),
    certifiedAttributes = Nil,
    declaredAttributes = Nil,
    suspendedByConsumer = None,
    suspendedByProducer = None,
    suspendedByPlatform = None,
    createdAt = OffsetDateTime.now(),
    updatedAt = None
  )

}
