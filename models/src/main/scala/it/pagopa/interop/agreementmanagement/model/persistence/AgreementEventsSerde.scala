package it.pagopa.interop.agreementmanagement.model.persistence

import it.pagopa.interop.agreementmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.commons.queue.message.ProjectableEvent
import spray.json._

object AgreementEventsSerde {

  val projectableAgreementToJson: PartialFunction[ProjectableEvent, JsValue] = { case event: Event =>
    agreementToJson(event)
  }

  def agreementToJson(event: Event): JsValue = event match {
    case x: AgreementContractAdded           => x.toJson
    case x: AgreementConsumerDocumentAdded   => x.toJson
    case x: AgreementConsumerDocumentRemoved => x.toJson
    case x: AgreementAdded                   => x.toJson
    case x: AgreementUpdated                 => x.toJson
    case x: AgreementActivated               => x.toJson
    case x: AgreementSuspended               => x.toJson
    case x: AgreementDeactivated             => x.toJson
    case x: AgreementDeleted                 => x.toJson
    case x: VerifiedAttributeUpdated         => x.toJson
  }

  val jsonToAgreement: PartialFunction[String, JsValue => ProjectableEvent] = {
    case `agreementContractAdded`           => _.convertTo[AgreementContractAdded]
    case `agreementConsumerDocumentAdded`   => _.convertTo[AgreementConsumerDocumentAdded]
    case `agreementConsumerDocumentRemoved` => _.convertTo[AgreementConsumerDocumentRemoved]
    case `agreementAdded`                   => _.convertTo[AgreementAdded]
    case `agreementActivated`               => _.convertTo[AgreementActivated]
    case `agreementSuspended`               => _.convertTo[AgreementSuspended]
    case `agreementDeactivated`             => _.convertTo[AgreementDeactivated]
    case `agreementUpdated`                 => _.convertTo[AgreementUpdated]
    case `agreementDeleted`                 => _.convertTo[AgreementDeleted]
    case `verifiedAttributeUpdated`         => _.convertTo[VerifiedAttributeUpdated]
  }

  def getKind(e: Event): String = e match {
    case _: AgreementContractAdded           => agreementContractAdded
    case _: AgreementConsumerDocumentAdded   => agreementConsumerDocumentAdded
    case _: AgreementConsumerDocumentRemoved => agreementConsumerDocumentRemoved
    case _: AgreementAdded                   => agreementAdded
    case _: AgreementActivated               => agreementActivated
    case _: AgreementSuspended               => agreementSuspended
    case _: AgreementDeactivated             => agreementDeactivated
    case _: AgreementUpdated                 => agreementUpdated
    case _: AgreementDeleted                 => agreementDeleted
    case _: VerifiedAttributeUpdated         => verifiedAttributeUpdated
  }

  private val agreementContractAdded: String           = "agreement_contract_added"
  private val agreementConsumerDocumentAdded: String   = "agreement_consumer_document_added"
  private val agreementConsumerDocumentRemoved: String = "agreement_consumer_document_removed"
  private val agreementAdded: String                   = "agreement_added"
  private val agreementDeleted: String                 = "agreement_deleted"
  private val agreementActivated: String               = "agreement_activated"
  private val agreementSuspended: String               = "agreement_suspended"
  private val agreementDeactivated: String             = "agreement_deactivated"
  private val agreementUpdated: String                 = "agreement_updated"
  private val verifiedAttributeUpdated: String         = "verified_attribute_updated"

}
