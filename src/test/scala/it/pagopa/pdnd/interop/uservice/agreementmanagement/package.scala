package it.pagopa.pdnd.interop.uservice

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.scaladsl.Source
import akka.util.ByteString
import it.pagopa.pdnd.interop.uservice.agreementmanagement.api.impl._
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.{Agreement, AgreementSeed, VerifiedAttributeSeed}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.service.UUIDSupplier
import org.scalamock.scalatest.MockFactory

import scala.concurrent.Await
import scala.concurrent.duration.Duration

package object agreementmanagement extends MockFactory {

  val uuidSupplier: UUIDSupplier = mock[UUIDSupplier]

  final lazy val url: String                  = "http://localhost:18088/pdnd-interop-uservice-agreement-management/0.0.1"
  final val authorization: Seq[Authorization] = Seq(headers.Authorization(OAuth2BearerToken("token")))

  implicit def fromEntityUnmarshallerAgreementSeed: ToEntityMarshaller[AgreementSeed] =
    sprayJsonMarshaller[AgreementSeed]

  implicit def fromEntityUnmarshallerVerifiedAttributeSeed: ToEntityMarshaller[VerifiedAttributeSeed] =
    sprayJsonMarshaller[VerifiedAttributeSeed]

  implicit def toEntityMarshallerAgreement: FromEntityUnmarshaller[Agreement] =
    sprayJsonUnmarshaller[Agreement]

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
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
