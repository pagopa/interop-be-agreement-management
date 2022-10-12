package it.pagopa.interop

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.scaladsl.Source
import akka.util.ByteString
import it.pagopa.interop.agreementmanagement.api.impl._
import it.pagopa.interop.agreementmanagement.model._
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import org.scalamock.scalatest.MockFactory

import java.net.InetAddress
import scala.concurrent.Await
import scala.concurrent.duration.Duration

package object agreementmanagement extends MockFactory {

  final lazy val url: String                =
    s"http://localhost:18088/agreement-management/${buildinfo.BuildInfo.interfaceVersion}"
  final val requestHeaders: Seq[HttpHeader] =
    Seq(
      headers.Authorization(OAuth2BearerToken("token")),
      headers.RawHeader("X-Correlation-Id", "test-id"),
      headers.`X-Forwarded-For`(RemoteAddress(InetAddress.getByName("127.0.0.1")))
    )

  val mockUUIDSupplier: UUIDSupplier               = mock[UUIDSupplier]
  val mockDateTimeSupplier: OffsetDateTimeSupplier = mock[OffsetDateTimeSupplier]

  val emptyData: Source[ByteString, NotUsed] = Source.empty[ByteString]

  implicit def toEntityMarshallerAgreementSeed: ToEntityMarshaller[AgreementSeed] =
    sprayJsonMarshaller[AgreementSeed]

  implicit def toEntityMarshallerUpdateAgreementSeed: ToEntityMarshaller[UpdateAgreementSeed] =
    sprayJsonMarshaller[UpdateAgreementSeed]

  implicit def toEntityMarshallerUpgradeAgreementSeed: ToEntityMarshaller[UpgradeAgreementSeed] =
    sprayJsonMarshaller[UpgradeAgreementSeed]

  implicit def toEntityMarshallerDocumentSeed: ToEntityMarshaller[DocumentSeed] =
    sprayJsonMarshaller[DocumentSeed]

  implicit def fromEntityUnmarshallerAgreements: FromEntityUnmarshaller[Seq[Agreement]] =
    sprayJsonUnmarshaller[Seq[Agreement]]

  implicit def fromEntityUnmarshallerAgreement: FromEntityUnmarshaller[Agreement] =
    sprayJsonUnmarshaller[Agreement]

  implicit def fromEntityUnmarshallerDocument: FromEntityUnmarshaller[Document] =
    sprayJsonUnmarshaller[Document]

  implicit def fromEntityUnmarshallerProblem: FromEntityUnmarshaller[Problem] =
    sprayJsonUnmarshaller[Problem]

  def makeRequest(data: Source[ByteString, Any], path: String, verb: HttpMethod)(implicit
    actorSystem: ActorSystem
  ): HttpResponse = {
    Await.result(
      Http().singleRequest(
        HttpRequest(
          uri = s"$url/$path",
          method = verb,
          entity = HttpEntity(ContentTypes.`application/json`, data),
          headers = requestHeaders
        )
      ),
      Duration.Inf
    )
  }
}
