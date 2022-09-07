package it.pagopa.interop.agreementmanagement.api

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCode
import akka.util.Timeout
import it.pagopa.interop.agreementmanagement.model.persistence.Command
import it.pagopa.interop.agreementmanagement.model._
import it.pagopa.interop.commons.utils.SprayCommonFormats.{offsetDateTimeFormat, uuidFormat}
import it.pagopa.interop.commons.utils.errors.ComponentError
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.duration.Duration

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val documentFormat: RootJsonFormat[Document]                         = jsonFormat6(Document)
  implicit val documentSeedFormat: RootJsonFormat[DocumentSeed]                 = jsonFormat4(DocumentSeed)
  implicit val verifiedAttributeFormat: RootJsonFormat[VerifiedAttribute]       = jsonFormat1(VerifiedAttribute)
  implicit val certifiedAttributeFormat: RootJsonFormat[CertifiedAttribute]     = jsonFormat1(CertifiedAttribute)
  implicit val declaredAttributeFormat: RootJsonFormat[DeclaredAttribute]       = jsonFormat1(DeclaredAttribute)
  implicit val attributeSeedFormat: RootJsonFormat[AttributeSeed]               = jsonFormat1(AttributeSeed)
  implicit val updateAgreementSeedFormat: RootJsonFormat[UpdateAgreementSeed]   = jsonFormat8(UpdateAgreementSeed)
  implicit val agreementSeedFormat: RootJsonFormat[AgreementSeed]               = jsonFormat8(AgreementSeed)
  implicit val upgradeAgreementSeedFormat: RootJsonFormat[UpgradeAgreementSeed] = jsonFormat1(UpgradeAgreementSeed)
  implicit val agreementFormat: RootJsonFormat[Agreement]                       = jsonFormat15(Agreement)
  implicit val problemErrorFormat: RootJsonFormat[ProblemError]                 = jsonFormat2(ProblemError)
  implicit val problemFormat: RootJsonFormat[Problem]                           = jsonFormat5(Problem)

  final val entityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

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

  final val serviceErrorCodePrefix: String = "004"
  final val defaultProblemType: String     = "about:blank"
  final val defaultErrorMessage: String    = "Unknown error"

  def problemOf(httpError: StatusCode, error: ComponentError): Problem =
    Problem(
      `type` = defaultProblemType,
      status = httpError.intValue,
      title = httpError.defaultMessage,
      errors = Seq(
        ProblemError(
          code = s"$serviceErrorCodePrefix-${error.code}",
          detail = Option(error.getMessage).getOrElse(defaultErrorMessage)
        )
      )
    )

  def problemOf(httpError: StatusCode, errors: List[ComponentError]): Problem =
    Problem(
      `type` = defaultProblemType,
      status = httpError.intValue,
      title = httpError.defaultMessage,
      errors = errors.map(error =>
        ProblemError(
          code = s"$serviceErrorCodePrefix-${error.code}",
          detail = Option(error.getMessage).getOrElse(defaultErrorMessage)
        )
      )
    )
}
