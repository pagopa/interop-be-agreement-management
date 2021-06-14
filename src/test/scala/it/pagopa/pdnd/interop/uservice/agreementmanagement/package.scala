package it.pagopa.pdnd.interop.uservice

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import it.pagopa.pdnd.interop.uservice.agreementmanagement.service.UUIDSupplier
import org.scalamock.scalatest.MockFactory

import scala.concurrent.Await
import scala.concurrent.duration.Duration


package object agreementmanagement extends MockFactory {

  val uuidSupplier: UUIDSupplier = mock[UUIDSupplier]

  final lazy val url: String = "http://localhost:18088/pdnd-interop-uservice-agreement-management/0.0.1"

  @SuppressWarnings(
    Array(
      "org.wartremover.warts.ImplicitParameter",
    )
  )
  def create(data: Source[ByteString, Any], path: String)(implicit actorSystem:ActorSystem): HttpResponse = {
    Await.result(
      Http().singleRequest(
        HttpRequest(
          uri = s"$url/$path",
          method = HttpMethods.POST,
          entity = HttpEntity(ContentTypes.`application/json`, data)
        )
      ),
      Duration.Inf
    )
  }
}
