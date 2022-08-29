package it.pagopa.interop.agreementmanagement.model.persistence

import it.pagopa.interop.agreementmanagement.model.agreement._
import it.pagopa.interop.commons.queue.message.ProjectableEvent
import it.pagopa.interop.commons.utils.SprayCommonFormats._
import spray.json.DefaultJsonProtocol._
import spray.json._
object AgreementEventsSerde {

  val agreementToJson: PartialFunction[ProjectableEvent, JsValue] = {
    case x: AgreementConsumerDocumentAdded   => x.toJson
    case x: AgreementConsumerDocumentRemoved => x.toJson
    case x: AgreementAdded                   => x.toJson
    case x: AgreementDeleted                 => x.toJson
    case x: AgreementSubmitted               => x.toJson
    case x: AgreementActivated               => x.toJson
    case x: AgreementSuspended               => x.toJson
    case x: AgreementDeactivated             => x.toJson
  }

  val jsonToAgreement: PartialFunction[String, JsValue => ProjectableEvent] = {
    case `agreementConsumerDocumentAdded`   => _.convertTo[AgreementConsumerDocumentAdded]
    case `agreementConsumerDocumentRemoved` => _.convertTo[AgreementConsumerDocumentRemoved]
    case `agreementAdded`                   => _.convertTo[AgreementAdded]
    case `agreementDeleted`                 => _.convertTo[AgreementDeleted]
    case `agreementSubmitted`               => _.convertTo[AgreementSubmitted]
    case `agreementActivated`               => _.convertTo[AgreementActivated]
    case `agreementSuspended`               => _.convertTo[AgreementSuspended]
    case `agreementDeactivated`             => _.convertTo[AgreementDeactivated]
  }

  def getKind(e: Event): String = e match {
    case _: AgreementConsumerDocumentAdded   => agreementConsumerDocumentAdded
    case _: AgreementConsumerDocumentRemoved => agreementConsumerDocumentRemoved
    case _: AgreementAdded                   => agreementAdded
    case _: AgreementDeleted                 => agreementDeleted
    case _: AgreementSubmitted               => agreementSubmitted
    case _: AgreementActivated               => agreementActivated
    case _: AgreementSuspended               => agreementSuspended
    case _: AgreementDeactivated             => agreementDeactivated
  }

  private val agreementConsumerDocumentAdded: String   = "agreement_consumer_document_added"
  private val agreementConsumerDocumentRemoved: String = "agreement_consumer_document_removed"
  private val agreementAdded: String                   = "agreement_added"
  private val agreementDeleted: String                 = "agreement_deleted"
  private val agreementSubmitted: String               = "agreement_submitted"
  private val agreementActivated: String               = "agreement_activated"
  private val agreementSuspended: String               = "agreement_suspended"
  private val agreementDeactivated: String             = "agreement_deactivated"

  private implicit val pasFormat: RootJsonFormat[PersistentAgreementState] =
    new RootJsonFormat[PersistentAgreementState] {
      override def read(json: JsValue): PersistentAgreementState = json match {
        case JsString("Draft")     => Draft
        case JsString("Pending")   => Pending
        case JsString("Active")    => Active
        case JsString("Suspended") => Suspended
        case JsString("Inactive ") => Inactive
        case _ => deserializationError("Unable to deserialize json as a PersistentPurposeVersionState")
      }

      override def write(obj: PersistentAgreementState): JsValue = obj match {
        case Draft     => JsString("Draft")
        case Pending   => JsString("Pending")
        case Active    => JsString("Active")
        case Suspended => JsString("Suspended")
        case Inactive  => JsString("Inactive")
      }
    }

  private implicit val pvadFormat: RootJsonFormat[PersistentAgreementDocument] =
    jsonFormat6(PersistentAgreementDocument)
  private implicit val pvaFormat: RootJsonFormat[PersistentVerifiedAttribute] = jsonFormat1(PersistentVerifiedAttribute)
  private implicit val pcaFormat: RootJsonFormat[PersistentCertifiedAttribute] =
    jsonFormat1(PersistentCertifiedAttribute)
  private implicit val pdaFormat: RootJsonFormat[PersistentDeclaredAttribute] = jsonFormat1(PersistentDeclaredAttribute)
  private implicit val paFormat: RootJsonFormat[PersistentAgreement]          = jsonFormat15(PersistentAgreement)
  private implicit val acdaFormat: RootJsonFormat[AgreementConsumerDocumentAdded]   =
    jsonFormat2(AgreementConsumerDocumentAdded)
  private implicit val acdrFormat: RootJsonFormat[AgreementConsumerDocumentRemoved] =
    jsonFormat2(AgreementConsumerDocumentRemoved)
  private implicit val aadFormat: RootJsonFormat[AgreementAdded]                    = jsonFormat1(AgreementAdded)
  private implicit val adelFormat: RootJsonFormat[AgreementDeleted]                 = jsonFormat1(AgreementDeleted)
  private implicit val asubFormat: RootJsonFormat[AgreementSubmitted]               = jsonFormat1(AgreementSubmitted)
  private implicit val aacFormat: RootJsonFormat[AgreementActivated]                = jsonFormat1(AgreementActivated)
  private implicit val asFormat: RootJsonFormat[AgreementSuspended]                 = jsonFormat1(AgreementSuspended)
  private implicit val adeaFormat: RootJsonFormat[AgreementDeactivated]             = jsonFormat1(AgreementDeactivated)

}
