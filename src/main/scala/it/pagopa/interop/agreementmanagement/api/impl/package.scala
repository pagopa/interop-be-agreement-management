package it.pagopa.interop.agreementmanagement.api

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCode
import akka.util.Timeout
import it.pagopa.interop.agreementmanagement.model.{
  Agreement,
  AgreementSeed,
  StateChangeDetails,
  VerifiedAttribute,
  VerifiedAttributeSeed
}
import it.pagopa.interop.agreementmanagement.model._
import it.pagopa.interop.agreementmanagement.model.persistence.Command
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import it.pagopa.interop.commons.utils.SprayCommonFormats.{offsetDateTimeFormat, uuidFormat}
import it.pagopa.interop.commons.utils.errors.ComponentError

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val verifiedAttributeFormat: RootJsonFormat[VerifiedAttribute]         = jsonFormat4(VerifiedAttribute)
  implicit val verifiedAttributeSeedFormat: RootJsonFormat[VerifiedAttributeSeed] = jsonFormat3(VerifiedAttributeSeed)
  implicit val agreementSeedFormat: RootJsonFormat[AgreementSeed]                 = jsonFormat5(AgreementSeed)
  implicit val agreementFormat: RootJsonFormat[Agreement]                         = jsonFormat11(Agreement)
  implicit val stateChangeDetailsFormat: RootJsonFormat[StateChangeDetails]       = jsonFormat1(StateChangeDetails)
  implicit val problemErrorFormat: RootJsonFormat[ProblemError]                   = jsonFormat2(ProblemError)
  implicit val problemFormat: RootJsonFormat[Problem]                             = jsonFormat5(Problem)

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

  def problemOf(httpError: StatusCode, error: ComponentError, defaultMessage: String = "Unknown error"): Problem =
    Problem(
      `type` = defaultProblemType,
      status = httpError.intValue,
      title = httpError.defaultMessage,
      errors = Seq(
        ProblemError(
          code = s"$serviceErrorCodePrefix-${error.code}",
          detail = Option(error.getMessage).getOrElse(defaultMessage)
        )
      )
    )
}
