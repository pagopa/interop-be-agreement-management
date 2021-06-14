package it.pagopa.pdnd.interop.uservice.agreementmanagement.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.{Agreement, AgreementSeed, Problem}
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat, deserializationError}

import java.util.UUID
import scala.util.{Failure, Success, Try}

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

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

  implicit val agreementSeedFormat: RootJsonFormat[AgreementSeed] = jsonFormat3(AgreementSeed)
  implicit val agreementFormat: RootJsonFormat[Agreement]         = jsonFormat5(Agreement)
  implicit val problemFormat: RootJsonFormat[Problem]             = jsonFormat3(Problem)

}
