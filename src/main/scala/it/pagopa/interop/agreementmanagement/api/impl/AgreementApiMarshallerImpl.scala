package it.pagopa.interop.agreementmanagement.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.agreementmanagement.api.AgreementApiMarshaller
import it.pagopa.interop.agreementmanagement.model._
import spray.json._

object AgreementApiMarshallerImpl extends AgreementApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def fromEntityUnmarshallerAgreementSeed: FromEntityUnmarshaller[AgreementSeed] =
    sprayJsonUnmarshaller[AgreementSeed]

  override implicit def toEntityMarshallerAgreementarray: ToEntityMarshaller[Seq[Agreement]] =
    sprayJsonMarshaller[Seq[Agreement]]

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem

  override implicit def fromEntityUnmarshallerUpdateAgreementSeed: FromEntityUnmarshaller[UpdateAgreementSeed] =
    sprayJsonUnmarshaller[UpdateAgreementSeed]

  override implicit def toEntityMarshallerAgreement: ToEntityMarshaller[Agreement] = sprayJsonMarshaller[Agreement]

  override implicit def fromEntityUnmarshallerUpgradeAgreementSeed: FromEntityUnmarshaller[UpgradeAgreementSeed] =
    sprayJsonUnmarshaller[UpgradeAgreementSeed]

  override implicit def fromEntityUnmarshallerDocumentSeed: FromEntityUnmarshaller[DocumentSeed] =
    sprayJsonUnmarshaller[DocumentSeed]

  override implicit def toEntityMarshallerDocument: ToEntityMarshaller[Document] = sprayJsonMarshaller[Document]
}
