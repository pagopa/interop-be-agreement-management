package it.pagopa.interop.agreementmanagement.model.persistence

import spray.json._
import spray.json.DefaultJsonProtocol._
import it.pagopa.interop.agreementmanagement.model.agreement._
import it.pagopa.interop.commons.utils.SprayCommonFormats._
import it.pagopa.interop.commons.queue.message.ProjectableEvent
object AgreementEventsSerde {

  val agreementToJson: PartialFunction[ProjectableEvent, JsValue] = {
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

  private implicit val pasFormat: RootJsonFormat[PersistentAgreementState] =
    new RootJsonFormat[PersistentAgreementState] {
      override def read(json: JsValue): PersistentAgreementState = json match {
        case JsString("Pending")   => Pending
        case JsString("Active")    => Active
        case JsString("Suspended") => Suspended
        case JsString("Inactive ") => Inactive
        case _ => deserializationError("Unable to deserialize json as a PersistentPurposeVersionState")
      }

      override def write(obj: PersistentAgreementState): JsValue = obj match {
        case Pending   => JsString("Pending")
        case Active    => JsString("Active")
        case Suspended => JsString("Suspended")
        case Inactive  => JsString("Inactive")
      }
    }

  private implicit val pvaFormat: RootJsonFormat[PersistentVerifiedAttribute] = jsonFormat4(
    PersistentVerifiedAttribute.apply
  )
  private implicit val paFormat: RootJsonFormat[PersistentAgreement]          = jsonFormat11(PersistentAgreement.apply)
  private implicit val vauFormat: RootJsonFormat[VerifiedAttributeUpdated] = jsonFormat1(VerifiedAttributeUpdated.apply)
  private implicit val aadFormat: RootJsonFormat[AgreementAdded]           = jsonFormat1(AgreementAdded.apply)
  private implicit val aacFormat: RootJsonFormat[AgreementActivated]       = jsonFormat1(AgreementActivated.apply)
  private implicit val asFormat: RootJsonFormat[AgreementSuspended]        = jsonFormat1(AgreementSuspended.apply)
  private implicit val adFormat: RootJsonFormat[AgreementDeactivated]      = jsonFormat1(AgreementDeactivated.apply)

}
