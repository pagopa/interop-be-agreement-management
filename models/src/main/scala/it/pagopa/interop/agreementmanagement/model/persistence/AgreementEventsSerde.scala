package it.pagopa.interop.agreementmanagement.model.persistence

import it.pagopa.interop.agreementmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.commons.queue.message.ProjectableEvent
import spray.json._

object AgreementEventsSerde {

  val projectableAgreementToJson: PartialFunction[ProjectableEvent, JsValue] = { case event: Event =>
    agreementToJson(event)
  }

  def agreementToJson(event: Event): JsValue = event match {
    case x @ VerifiedAttributeUpdated(_) => x.toJson
    case x @ AgreementAdded(_)           => x.toJson
    case x @ AgreementActivated(_)       => x.toJson
    case x @ AgreementSuspended(_)       => x.toJson
    case x @ AgreementDeactivated(_)     => x.toJson
  }

  val jsonToAgreement: PartialFunction[String, JsValue => ProjectableEvent] = {
    case `verifiedAttributeUpdated` => _.convertTo[VerifiedAttributeUpdated]
    case `agreementAdded`           => _.convertTo[AgreementAdded]
    case `agreementActivated`       => _.convertTo[AgreementActivated]
    case `agreementSuspended`       => _.convertTo[AgreementSuspended]
    case `agreementDeactivated`     => _.convertTo[AgreementDeactivated]
  }

  def getKind(e: Event): String = e match {
    case VerifiedAttributeUpdated(_) => verifiedAttributeUpdated
    case AgreementAdded(_)           => agreementAdded
    case AgreementActivated(_)       => agreementActivated
    case AgreementSuspended(_)       => agreementSuspended
    case AgreementDeactivated(_)     => agreementDeactivated
  }

  private val verifiedAttributeUpdated: String = "verified_attribute_updated"
  private val agreementAdded: String           = "agreement_added"
  private val agreementActivated: String       = "agreement_activated"
  private val agreementSuspended: String       = "agreement_suspended"
  private val agreementDeactivated: String     = "agreement_deactivated"

}
