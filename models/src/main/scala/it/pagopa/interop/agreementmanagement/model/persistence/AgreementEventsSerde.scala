package it.pagopa.interop.agreementmanagement.model.persistence

import it.pagopa.interop.agreementmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.commons.queue.message.ProjectableEvent
import spray.json._

object AgreementEventsSerde {

  val projectableAgreementToJson: PartialFunction[ProjectableEvent, JsValue] = { case event: Event =>
    agreementToJson(event)
  }

  def agreementToJson(event: Event): JsValue = event match {
    case x: AgreementConsumerDocumentAdded   => x.toJson
    case x: AgreementConsumerDocumentRemoved => x.toJson
    case x: AgreementAdded                   => x.toJson
    case x: AgreementActivated               => x.toJson
    case x: AgreementSuspended               => x.toJson
    case x: AgreementDeactivated             => x.toJson
  }

  val jsonToAgreement: PartialFunction[String, JsValue => ProjectableEvent] = {
    case `agreementConsumerDocumentAdded`   => _.convertTo[AgreementConsumerDocumentAdded]
    case `agreementConsumerDocumentRemoved` => _.convertTo[AgreementConsumerDocumentRemoved]
    case `agreementAdded`                   => _.convertTo[AgreementAdded]
    case `agreementActivated`               => _.convertTo[AgreementActivated]
    case `agreementSuspended`               => _.convertTo[AgreementSuspended]
    case `agreementDeactivated`             => _.convertTo[AgreementDeactivated]
  }

  def getKind(e: Event): String = e match {
    case _: AgreementConsumerDocumentAdded   => agreementConsumerDocumentAdded
    case _: AgreementConsumerDocumentRemoved => agreementConsumerDocumentRemoved
    case _: AgreementAdded                   => agreementAdded
    case _: AgreementActivated               => agreementActivated
    case _: AgreementSuspended               => agreementSuspended
    case _: AgreementDeactivated             => agreementDeactivated
  }

  private val agreementConsumerDocumentAdded: String   = "agreement_consumer_document_added"
  private val agreementConsumerDocumentRemoved: String = "agreement_consumer_document_removed"
  private val agreementAdded: String                   = "agreement_added"
  private val agreementActivated: String               = "agreement_activated"
  private val agreementSuspended: String               = "agreement_suspended"
  private val agreementDeactivated: String             = "agreement_deactivated"

}
