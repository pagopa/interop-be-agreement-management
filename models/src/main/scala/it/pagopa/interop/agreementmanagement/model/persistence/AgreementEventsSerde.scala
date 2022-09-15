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
    case x: AgreementUpdated                 => x.toJson
    case x: AgreementDeleted                 => x.toJson
    case x: VerifiedAttributeUpdated         => x.toJson
  }

  val jsonToAgreement: PartialFunction[String, JsValue => ProjectableEvent] = {
    case `agreementConsumerDocumentAdded`   => _.convertTo[AgreementConsumerDocumentAdded]
    case `agreementConsumerDocumentRemoved` => _.convertTo[AgreementConsumerDocumentRemoved]
    case `agreementAdded`                   => _.convertTo[AgreementAdded]
    case `agreementUpdated`                 => _.convertTo[AgreementUpdated]
    case `agreementDeleted`                 => _.convertTo[AgreementDeleted]
    case `verifiedAttributeUpdated`         => _.convertTo[VerifiedAttributeUpdated]
  }

  def getKind(e: Event): String = e match {
    case _: AgreementConsumerDocumentAdded   => agreementConsumerDocumentAdded
    case _: AgreementConsumerDocumentRemoved => agreementConsumerDocumentRemoved
    case _: AgreementAdded                   => agreementAdded
    case _: AgreementUpdated                 => agreementUpdated
    case _: AgreementDeleted                 => agreementDeleted
    case _: VerifiedAttributeUpdated         => verifiedAttributeUpdated
  }

  private val agreementConsumerDocumentAdded: String   = "agreement_consumer_document_added"
  private val agreementConsumerDocumentRemoved: String = "agreement_consumer_document_removed"
  private val agreementAdded: String                   = "agreement_added"
  private val agreementDeleted: String                 = "agreement_deleted"
  private val agreementUpdated: String                 = "agreement_updated"
  private val verifiedAttributeUpdated: String         = "verified-attribute-updated"

}
