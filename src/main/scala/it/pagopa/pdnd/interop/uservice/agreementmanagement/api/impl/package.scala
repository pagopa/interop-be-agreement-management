package it.pagopa.pdnd.interop.uservice.agreementmanagement.api

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCode
import akka.util.Timeout
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model._
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence.Command
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import it.pagopa.pdnd.interop.commons.utils.SprayCommonFormats.{offsetDateTimeFormat, uuidFormat}

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val verifiedAttributeFormat: RootJsonFormat[VerifiedAttribute]         = jsonFormat4(VerifiedAttribute)
  implicit val verifiedAttributeSeedFormat: RootJsonFormat[VerifiedAttributeSeed] = jsonFormat3(VerifiedAttributeSeed)
  implicit val agreementSeedFormat: RootJsonFormat[AgreementSeed]                 = jsonFormat5(AgreementSeed)
  implicit val agreementFormat: RootJsonFormat[Agreement]                         = jsonFormat9(Agreement)
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

  def problemOf(
    httpError: StatusCode,
    errorCode: String,
    exception: Throwable = new RuntimeException(),
    defaultMessage: String = "Unknown error"
  ): Problem =
    Problem(
      `type` = "about:blank",
      status = httpError.intValue,
      title = httpError.defaultMessage,
      errors =
        Seq(ProblemError(code = s"004-$errorCode", detail = Option(exception.getMessage).getOrElse(defaultMessage)))
    )
}
