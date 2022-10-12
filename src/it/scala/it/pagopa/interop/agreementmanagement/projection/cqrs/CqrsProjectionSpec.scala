package it.pagopa.interop.agreementmanagement.projection.cqrs

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import it.pagopa.interop.agreementmanagement.ItSpecData._
import it.pagopa.interop.agreementmanagement.model.agreement.{Active, Pending, PersistentAgreement}
import it.pagopa.interop.agreementmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.agreementmanagement.{ItSpecConfiguration, ItSpecHelper}

class CqrsProjectionSpec extends ScalaTestWithActorTestKit(ItSpecConfiguration.config) with ItSpecHelper {

  "Projection" should {
    "succeed for event AgreementAdded" in {
      val expected  = createAgreement(persistentAgreement)
      val persisted = findOne[PersistentAgreement](expected.id.toString).futureValue

      compareAgreements(expected, persisted)
    }

    "succeed for event AgreementUpdated" in {
      val agreement = createAgreement(persistentAgreement.copy(state = Active))
      val expected  = updateAgreement(agreement.copy(state = Pending))
      val persisted = findOne[PersistentAgreement](expected.id.toString).futureValue

      compareAgreements(expected, persisted)
    }

    "succeed for event AgreementDeleted" in {
      val agreement = createAgreement(persistentAgreement)
      val _         = deletedAgreement(agreement.id.toString)
      val persisted = find[PersistentAgreement](agreement.id.toString).futureValue

      persisted shouldBe Seq.empty
    }

    "succeed for event AgreementContractAdded" in {
      val agreement = createAgreement(persistentAgreement.copy(state = Active))

      val document = persistentDocument

      val createdDoc = addContract(agreement.id, document)
      val expected   = agreement.copy(contract = Some(createdDoc))
      val persisted  = findOne[PersistentAgreement](expected.id.toString).futureValue

      compareAgreements(expected, persisted)
    }

    "succeed for event AgreementConsumerDocumentAdded" in {
      val agreement = createAgreement(persistentAgreement.copy(state = Active))

      val document = persistentDocument

      val createdDoc = addConsumerDocument(agreement.id, document)
      val expected   = agreement.copy(consumerDocuments = agreement.consumerDocuments :+ createdDoc)
      val persisted  = findOne[PersistentAgreement](expected.id.toString).futureValue

      compareAgreements(expected, persisted)
    }

    "succeed for event AgreementConsumerDocumentRemoved" in {
      val document          = persistentDocument
      val existingAgreement =
        persistentAgreement.copy(state = Active, consumerDocuments = Seq(persistentDocument, document))
      val agreement         = createAgreement(existingAgreement)

      removeConsumerDocument(agreement.id, document.id)
      val expected  = agreement.copy(consumerDocuments = agreement.consumerDocuments.filter(_.id != document.id))
      val persisted = findOne[PersistentAgreement](expected.id.toString).futureValue

      compareAgreements(expected, persisted)
    }

  }

}
