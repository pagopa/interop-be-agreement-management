package it.pagopa.interop.agreementmanagement.projection.cqrs

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import it.pagopa.interop.agreementmanagement.ItSpecData._
import it.pagopa.interop.agreementmanagement.model.ChangedBy.PRODUCER
import it.pagopa.interop.agreementmanagement.model.agreement.{Active, Pending, PersistentAgreement}
import it.pagopa.interop.agreementmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.agreementmanagement.model.{StateChangeDetails, VerifiedAttributeSeed}
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

    "succeed for event VerifiedAttributeUpdated" in {
      val agreement = createAgreement(persistentAgreement.copy(state = Active))

      val existingAttribute = agreement.verifiedAttributes.head
      val seed              = VerifiedAttributeSeed(
        id = existingAttribute.id,
        verified = existingAttribute.verified.map(!_),
        validityTimespan = existingAttribute.validityTimespan.map(_ + 10)
      )

      val expected  = updateVerifiedAttribute(agreement.id, seed)
      val persisted = findOne[PersistentAgreement](expected.id.toString).futureValue

      compareAgreements(expected, persisted)
    }

  }

}
