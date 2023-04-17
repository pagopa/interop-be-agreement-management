package it.pagopa.interop.agreementmanagement.model.persistence

import it.pagopa.interop.agreementmanagement.model.agreement._
import it.pagopa.interop.commons.utils.SprayCommonFormats._
import spray.json.DefaultJsonProtocol._
import spray.json._

object JsonFormats {
  implicit val pasFormat: RootJsonFormat[PersistentAgreementState] =
    new RootJsonFormat[PersistentAgreementState] {
      override def read(json: JsValue): PersistentAgreementState = json match {
        case JsString("Draft")                      => Draft
        case JsString("Pending")                    => Pending
        case JsString("Active")                     => Active
        case JsString("Suspended")                  => Suspended
        case JsString("Archived")                   => Archived
        case JsString("MissingCertifiedAttributes") => MissingCertifiedAttributes
        case JsString("Rejected")                   => Rejected
        case _ => deserializationError("Unable to deserialize json as a PersistentPurposeVersionState")
      }

      override def write(obj: PersistentAgreementState): JsValue = obj match {
        case Draft                      => JsString("Draft")
        case Pending                    => JsString("Pending")
        case Active                     => JsString("Active")
        case Suspended                  => JsString("Suspended")
        case Archived                   => JsString("Archived")
        case MissingCertifiedAttributes => JsString("MissingCertifiedAttributes")
        case Rejected                   => JsString("Rejected")
      }
    }

  implicit val pcaFormat: RootJsonFormat[PersistentCertifiedAttribute] = jsonFormat1(PersistentCertifiedAttribute.apply)
  implicit val pdaFormat: RootJsonFormat[PersistentDeclaredAttribute]  = jsonFormat1(PersistentDeclaredAttribute.apply)
  implicit val pvaFormat: RootJsonFormat[PersistentVerifiedAttribute]  = jsonFormat1(PersistentVerifiedAttribute.apply)
  implicit val padFormat: RootJsonFormat[PersistentAgreementDocument]  = jsonFormat6(PersistentAgreementDocument.apply)
  implicit val psFormat: RootJsonFormat[PersistentStamp]               = jsonFormat2(PersistentStamp.apply)
  implicit val pssFormat: RootJsonFormat[PersistentStamps]             = jsonFormat7(PersistentStamps.apply)
  implicit val paFormat: RootJsonFormat[PersistentAgreement]           = jsonFormat20(PersistentAgreement.apply)
  implicit val aadFormat: RootJsonFormat[AgreementAdded]               = jsonFormat1(AgreementAdded.apply)
  implicit val aUpFormat: RootJsonFormat[AgreementUpdated]             = jsonFormat1(AgreementUpdated)
  implicit val aAcFormat: RootJsonFormat[AgreementActivated]           = jsonFormat1(AgreementActivated)
  implicit val aSuFormat: RootJsonFormat[AgreementSuspended]           = jsonFormat1(AgreementSuspended)
  implicit val aDeaFormat: RootJsonFormat[AgreementDeactivated]        = jsonFormat1(AgreementDeactivated)
  implicit val adelFormat: RootJsonFormat[AgreementDeleted]            = jsonFormat1(AgreementDeleted)
  implicit val vapFormat: RootJsonFormat[VerifiedAttributeUpdated]     = jsonFormat1(VerifiedAttributeUpdated)
  implicit val acdaFormat: RootJsonFormat[AgreementConsumerDocumentAdded]   =
    jsonFormat2(AgreementConsumerDocumentAdded.apply)
  implicit val acdrFormat: RootJsonFormat[AgreementConsumerDocumentRemoved] =
    jsonFormat2(AgreementConsumerDocumentRemoved.apply)
  implicit val ada: RootJsonFormat[AgreementContractAdded]                  = jsonFormat2(AgreementContractAdded.apply)
}
