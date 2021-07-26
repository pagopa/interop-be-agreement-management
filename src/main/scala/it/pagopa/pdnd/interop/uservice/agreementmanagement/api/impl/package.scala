package it.pagopa.pdnd.interop.uservice.agreementmanagement.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.{
  Agreement,
  AgreementSeed,
  Problem,
  VerifiedAttribute,
  VerifiedAttributeSeed
}
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat, deserializationError}

import java.time.{LocalDateTime, OffsetDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.UUID
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
  implicit val agreementSeedFormat: RootJsonFormat[AgreementSeed]                 = jsonFormat4(AgreementSeed)
  implicit val agreementFormat: RootJsonFormat[Agreement]                         = jsonFormat6(Agreement)
  implicit val problemFormat: RootJsonFormat[Problem]                             = jsonFormat3(Problem)

}
