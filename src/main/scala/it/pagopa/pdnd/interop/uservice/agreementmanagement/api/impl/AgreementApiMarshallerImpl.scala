package it.pagopa.pdnd.interop.uservice.agreementmanagement.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.pdnd.interop.uservice.agreementmanagement.api.AgreementApiMarshaller
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model._
import spray.json._

class AgreementApiMarshallerImpl extends AgreementApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def fromEntityUnmarshallerAgreementSeed: FromEntityUnmarshaller[AgreementSeed] =
    sprayJsonUnmarshaller[AgreementSeed]

  override implicit def toEntityMarshallerAgreementarray: ToEntityMarshaller[Seq[Agreement]] =
    sprayJsonMarshaller[Seq[Agreement]]

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

  override implicit def toEntityMarshallerAgreement: ToEntityMarshaller[Agreement] = sprayJsonMarshaller[Agreement]

  override implicit def fromEntityUnmarshallerVerifiedAttributeSeed: FromEntityUnmarshaller[VerifiedAttributeSeed] =
    sprayJsonUnmarshaller[VerifiedAttributeSeed]

  override implicit def fromEntityUnmarshallerStateChangeDetails: FromEntityUnmarshaller[StateChangeDetails] =
    sprayJsonUnmarshaller[StateChangeDetails]

}
