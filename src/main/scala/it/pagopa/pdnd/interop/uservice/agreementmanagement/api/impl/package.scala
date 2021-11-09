package it.pagopa.pdnd.interop.uservice.agreementmanagement.api

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.util.Timeout
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model._
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence.Command
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat, deserializationError}

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, OffsetDateTime, ZoneOffset}
import java.util.UUID
import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val localTimeFormat: JsonFormat[OffsetDateTime] = new JsonFormat[OffsetDateTime] {

    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val deserializationErrorMessage =
      s"Expected date time in ISO offset date time format ex. ${OffsetDateTime.now().format(formatter)}"

    override def write(obj: OffsetDateTime): JsValue = JsString(formatter.format(obj))

    override def read(json: JsValue): OffsetDateTime = {
      json match {
        case JsString(lTString) =>
          Try(OffsetDateTime.of(LocalDateTime.parse(lTString, formatter), ZoneOffset.UTC))
            .getOrElse(deserializationError(deserializationErrorMessage))
        case _ => deserializationError(deserializationErrorMessage)
      }
    }
  }

  implicit val uuidFormat: JsonFormat[UUID] =
    new JsonFormat[UUID] {
      override def write(obj: UUID): JsValue = JsString(obj.toString)

      override def read(json: JsValue): UUID = json match {
        case JsString(s) =>
          Try(UUID.fromString(s)) match {
            case Success(result) => result
            case Failure(exception) =>
              deserializationError(s"could not parse $s as UUID", exception)
          }
        case notAJsString =>
          deserializationError(s"expected a String but got a ${notAJsString.compactPrint}")
      }
    }

  implicit val verifiedAttributeFormat: RootJsonFormat[VerifiedAttribute]         = jsonFormat4(VerifiedAttribute)
  implicit val verifiedAttributeSeedFormat: RootJsonFormat[VerifiedAttributeSeed] = jsonFormat3(VerifiedAttributeSeed)
  implicit val agreementSeedFormat: RootJsonFormat[AgreementSeed]                 = jsonFormat5(AgreementSeed)
  implicit val agreementFormat: RootJsonFormat[Agreement]                         = jsonFormat9(Agreement)
  implicit val stateChangeDetailsFormat: RootJsonFormat[StateChangeDetails]       = jsonFormat1(StateChangeDetails)
  implicit val problemFormat: RootJsonFormat[Problem]                             = jsonFormat3(Problem)

  // This function could be candidate for the commons library
  @SuppressWarnings(
    Array("org.wartremover.warts.ImplicitParameter", "org.wartremover.warts.Any", "org.wartremover.warts.Nothing")
  )
  def slices[A, B <: Command](commander: EntityRef[B], sliceSize: Int)(
    commandGenerator: (Int, Int) => ActorRef[Seq[A]] => B
  )(implicit timeout: Timeout): LazyList[A] = {
    @tailrec
    def readSlice(commander: EntityRef[B], from: Int, to: Int, lazyList: LazyList[A]): LazyList[A] = {

      val slice: Seq[A] = Await.result(commander.ask(commandGenerator(from, to)), Duration.Inf)

      if (slice.isEmpty) lazyList
      else readSlice(commander, to, to + sliceSize, slice.to(LazyList) #::: lazyList)
    }
    readSlice(commander, 0, sliceSize, LazyList.empty)
  }

}
