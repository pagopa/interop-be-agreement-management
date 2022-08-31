package it.pagopa.interop.agreementmanagement.projection.cqrs

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import it.pagopa.interop.agreementmanagement.ItSpecData._
import it.pagopa.interop.agreementmanagement.model.ChangedBy.PRODUCER
import it.pagopa.interop.agreementmanagement.model.StateChangeDetails
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

    "succeed for event AgreementActivated" in {
      val stateChangeDetails = StateChangeDetails(changedBy = Some(PRODUCER))
      val agreement          = createAgreement(persistentAgreement.copy(state = Pending))

      val expected  = activateAgreement(agreement.id, stateChangeDetails)
      val persisted = findOne[PersistentAgreement](expected.id.toString).futureValue

      compareAgreements(expected, persisted)
    }

    "succeed for event AgreementSuspended" in {
      val stateChangeDetails = StateChangeDetails(changedBy = Some(PRODUCER))
      val agreement          = createAgreement(persistentAgreement.copy(state = Active))

      val expected  = suspendAgreement(agreement.id, stateChangeDetails)
      val persisted = findOne[PersistentAgreement](expected.id.toString).futureValue

      compareAgreements(expected, persisted)
    }

    "succeed for event AgreementDeactivated" in {
      val stateChangeDetails = StateChangeDetails(changedBy = Some(PRODUCER))
      val agreement          = createAgreement(persistentAgreement.copy(state = Active))

      val expected  = deactivateAgreement(agreement.id, stateChangeDetails)
      val persisted = findOne[PersistentAgreement](expected.id.toString).futureValue

      compareAgreements(expected, persisted)
    }

    "succeed for event AgreementConsumerDocumentAdded" in {
      val agreement = createAgreement(persistentAgreement.copy(state = Active))

      val document = persistentConsumerDocument

      val createdDoc = addConsumerDocument(agreement.id, document)
      val expected   = agreement.copy(consumerDocuments = agreement.consumerDocuments :+ createdDoc)
      val persisted  = findOne[PersistentAgreement](expected.id.toString).futureValue

      compareAgreements(expected, persisted)
    }

    "succeed for event AgreementConsumerDocumentRemoved" in {
      val document          = persistentConsumerDocument
      val existingAgreement =
        persistentAgreement.copy(state = Active, consumerDocuments = Seq(persistentConsumerDocument, document))
      val agreement         = createAgreement(existingAgreement)

      removeConsumerDocument(agreement.id, document.id)
      val expected  = agreement.copy(consumerDocuments = agreement.consumerDocuments.filter(_.id != document.id))
      val persisted = findOne[PersistentAgreement](expected.id.toString).futureValue

      compareAgreements(expected, persisted)
    }

  }

}
