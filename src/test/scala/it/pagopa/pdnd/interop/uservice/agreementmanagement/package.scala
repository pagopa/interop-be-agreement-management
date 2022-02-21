package it.pagopa.pdnd.interop.uservice

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.scaladsl.Source
import akka.util.ByteString
import it.pagopa.pdnd.interop.uservice.agreementmanagement.api.impl._
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.{
  Agreement,
  AgreementSeed,
  StateChangeDetails,
  VerifiedAttributeSeed
}
import it.pagopa.pdnd.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import org.scalamock.scalatest.MockFactory

import scala.concurrent.Await
import scala.concurrent.duration.Duration

package object agreementmanagement extends MockFactory {

  final lazy val url: String =
    s"http://localhost:18088/pdnd-interop-uservice-agreement-management/${buildinfo.BuildInfo.interfaceVersion}"
  final val authorization: Seq[Authorization] = Seq(headers.Authorization(OAuth2BearerToken("token")))

  val mockUUIDSupplier: UUIDSupplier               = mock[UUIDSupplier]
  val mockDateTimeSupplier: OffsetDateTimeSupplier = mock[OffsetDateTimeSupplier]

  val emptyData: Source[ByteString, NotUsed] = Source.empty[ByteString]

  implicit def toEntityMarshallerAgreementSeed: ToEntityMarshaller[AgreementSeed] =
    sprayJsonMarshaller[AgreementSeed]

  implicit def toEntityMarshallerStateChangeDetailsSeed: ToEntityMarshaller[StateChangeDetails] =
    sprayJsonMarshaller[StateChangeDetails]

  implicit def toEntityMarshallerVerifiedAttributeSeed: ToEntityMarshaller[VerifiedAttributeSeed] =
    sprayJsonMarshaller[VerifiedAttributeSeed]

  implicit def fromEntityUnmarshallerAgreements: FromEntityUnmarshaller[Seq[Agreement]] =
    sprayJsonUnmarshaller[Seq[Agreement]]

  implicit def fromEntityUnmarshallerAgreement: FromEntityUnmarshaller[Agreement] =
    sprayJsonUnmarshaller[Agreement]

  def makeRequest(data: Source[ByteString, Any], path: String, verb: HttpMethod)(implicit
    actorSystem: ActorSystem
  ): HttpResponse = {
    Await.result(
      Http().singleRequest(
        HttpRequest(
          uri = s"$url/$path",
          method = verb,
          entity = HttpEntity(ContentTypes.`application/json`, data),
          headers = authorization
        )
      ),
      Duration.Inf
    )
  }
}
