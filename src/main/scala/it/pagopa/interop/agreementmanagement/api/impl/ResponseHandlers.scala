package it.pagopa.interop.agreementmanagement.api.impl

import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LoggerTakingImplicit
import it.pagopa.interop.agreementmanagement.error.AgreementManagementErrors._
import it.pagopa.interop.commons.logging.ContextFieldsToLog
import it.pagopa.interop.commons.utils.errors.{AkkaResponses, ServiceCode}

import scala.util.{Failure, Success, Try}

object ResponseHandlers extends AkkaResponses {

  implicit val serviceCode: ServiceCode = ServiceCode("004")

  def getAgreementResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                     => success(s)
      case Failure(ex: AgreementNotFound) => notFound(ex, logMessage)
      case Failure(ex)                    => internalServerError(ex, logMessage)
    }

  def getAgreementsResponse[T](logMessage: String)(success: T => Route)(
    result: Either[Throwable, T]
  )(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Right(s) => success(s)
      case Left(ex) => internalServerError(ex, logMessage)
    }

  def addAgreementResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                     => success(s)
      case Failure(ex: AgreementConflict) => conflict(ex, logMessage)
      case Failure(ex)                    => internalServerError(ex, logMessage)
    }

  def updateAgreementByIdResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                     => success(s)
      case Failure(ex: AgreementNotFound) => notFound(ex, logMessage)
      case Failure(ex)                    => internalServerError(ex, logMessage)
    }

  def deleteAgreementResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                     => success(s)
      case Failure(ex: AgreementNotFound) => notFound(ex, logMessage)
      case Failure(ex)                    => internalServerError(ex, logMessage)
    }

  def addAgreementContactResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                  => success(s)
      case Failure(ex: AgreementNotFound)              => notFound(ex, logMessage)
      case Failure(ex: AgreementDocumentAlreadyExists) => conflict(ex, logMessage)
      case Failure(ex)                                 => internalServerError(ex, logMessage)
    }

  def addAgreementConsumerDocumentResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                  => success(s)
      case Failure(ex: AgreementNotFound)              => notFound(ex, logMessage)
      case Failure(ex: AgreementDocumentAlreadyExists) => conflict(ex, logMessage)
      case Failure(ex)                                 => internalServerError(ex, logMessage)
    }

  def removeAgreementConsumerDocumentResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                     => success(s)
      case Failure(ex: AgreementNotFound) => notFound(ex, logMessage)
      case Failure(ex)                    => internalServerError(ex, logMessage)
    }

  def getAgreementConsumerDocumentResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                             => success(s)
      case Failure(ex: AgreementNotFound)         => notFound(ex, logMessage)
      case Failure(ex: AgreementDocumentNotFound) => notFound(ex, logMessage)
      case Failure(ex)                            => internalServerError(ex, logMessage)
    }

  def upgradeAgreementByIdResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                               => success(s)
      case Failure(ex: AgreementNotInExpectedState) => badRequest(ex, logMessage)
      case Failure(ex: AgreementNotFound)           => notFound(ex, logMessage)
      case Failure(ex)                              => internalServerError(ex, logMessage)
    }

}
