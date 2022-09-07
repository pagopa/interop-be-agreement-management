package it.pagopa.interop.agreementmanagement.api.impl

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.{Route, StandardRoute}
import akka.pattern.StatusReply
import akka.util.Timeout
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementmanagement.api.AgreementApiService
import it.pagopa.interop.agreementmanagement.error.AgreementManagementErrors._
import it.pagopa.interop.agreementmanagement.model.AgreementState.ARCHIVED
import it.pagopa.interop.agreementmanagement.model._
import it.pagopa.interop.agreementmanagement.model.agreement._
import it.pagopa.interop.agreementmanagement.model.persistence.Adapters._
import it.pagopa.interop.agreementmanagement.model.persistence._
import it.pagopa.interop.commons.jwt._
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.OperationForbidden
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Success}

final case class AgreementApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]],
  UUIDSupplier: UUIDSupplier,
  dateTimeSupplier: OffsetDateTimeSupplier
)(implicit ec: ExecutionContext)
    extends AgreementApiService {

  val logger: LoggerTakingImplicit[ContextFieldsToLog] = Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  implicit val timeout: Timeout = 300.seconds

  private val settings: ClusterShardingSettings = entity.settings.getOrElse(ClusterShardingSettings(system))

  @inline private def getShard(id: String): String = Math.abs(id.hashCode % settings.numberOfShards).toString

  private[this] def authorize(roles: String*)(
    route: => Route
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
    authorizeInterop(hasPermissions(roles: _*), problemOf(StatusCodes.Forbidden, OperationForbidden)) {
      route
    }

  /**
   * Code: 200, Message: Agreement created, DataType: Agreement
   * Code: 400, Message: Invalid input, DataType: Problem
   * Code: 409, Message: Agreement already exists, DataType: Problem
   */
  override def addAgreement(agreementSeed: AgreementSeed)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {

    val operationLabel: String = s"Adding an agreement with date $agreementSeed"

    logger.info(
      s"$operationLabel for consumer ${agreementSeed.consumerId} to descriptor ${agreementSeed.descriptorId} " +
        s"of e-service ${agreementSeed.eserviceId} from the producer ${agreementSeed.producerId}"
    )
    val agreement: PersistentAgreement = PersistentAgreement.fromAPI(agreementSeed, UUIDSupplier, dateTimeSupplier)
    val result: Future[StatusReply[PersistentAgreement]] = createAgreement(agreement)

    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        addAgreement200(PersistentAgreement.toAPI(statusReply.getValue))
      case Success(statusReply)                          =>
        logger.error(
          s"Error while $operationLabel  for consumer ${agreementSeed.consumerId} to descriptor ${agreementSeed.descriptorId} " +
            s"of e-service ${agreementSeed.eserviceId} from the producer ${agreementSeed.producerId} ",
          statusReply.getError
        )
        statusReply.getError match {
          case ex: AgreementConflict => addAgreement409(problemOf(StatusCodes.Conflict, ex))
          case ex                    => internalServerError(operationLabel, ex.getMessage)
        }
      case Failure(ex)                                   =>
        logger.error(
          s"Error while $operationLabel  for consumer ${agreementSeed.consumerId} to descriptor ${agreementSeed.descriptorId} " +
            s"of e-service ${agreementSeed.eserviceId} from the producer ${agreementSeed.producerId} ",
          ex
        )
        internalServerError(operationLabel, ex.getMessage)
    }
  }

  private def createAgreement(agreement: PersistentAgreement): Future[StatusReply[PersistentAgreement]] =
    commander(agreement.id.toString).ask(ref => AddAgreement(agreement, ref))

  /**
   * Code: 200, Message: Agreement updated., DataType: Agreement
   * Code: 404, Message: Agreement not found, DataType: Problem
   * Code: 400, Message: Invalid ID supplied, DataType: Problem
   */
  override def updateAgreementById(agreementId: String, updateAgreementSeed: UpdateAgreementSeed)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {

    val operationLabel: String = s"Updating agreement $agreementId"

    logger.info(operationLabel)

    val result: Future[PersistentAgreement] =
      for {
        agreement <- commander(agreementId).askWithStatus(ref => GetAgreement(agreementId, ref))
        updated = PersistentAgreement.update(agreement, updateAgreementSeed, dateTimeSupplier)
        result <- commander(agreementId).askWithStatus(ref => UpdateAgreement(updated, ref))
      } yield result

    onComplete(result) {
      case Success(agreement)             => updateAgreementById200(PersistentAgreement.toAPI(agreement))
      case Failure(ex: AgreementNotFound) =>
        logger.error(s"Error while $operationLabel", ex)
        updateAgreementById404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex)                    =>
        logger.error(s"Error while $operationLabel", ex)
        internalServerError(operationLabel, ex.getMessage)
    }
  }

  /**
   * Code: 204, Message: Agreement deleted
   * Code: 400, Message: Invalid input, DataType: Problem
   * Code: 404, Message: Agreement not found, DataType: Problem
   */
  override def deleteAgreement(
    agreementId: String
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem], contexts: Seq[(String, String)]): Route =
    authorize(ADMIN_ROLE) {
      val operationLabel: String = s"Deleting agreement $agreementId"

      logger.info(operationLabel)

      val result: Future[StatusReply[Unit]] =
        commander(agreementId).ask(ref => DeleteAgreement(agreementId, ref))

      onComplete(result) {
        case Success(statusReply) if statusReply.isSuccess => deleteAgreement204
        case Success(statusReply)                          =>
          logger.error(s"Error while $operationLabel $agreementId", statusReply.getError)
          statusReply.getError match {
            case ex: AgreementNotFound => deleteAgreement404(problemOf(StatusCodes.NotFound, ex))
            case ex                    => internalServerError(operationLabel, ex.getMessage)
          }
        case Failure(ex)                                   =>
          logger.error(s"Error while $operationLabel $agreementId", ex)
          internalServerError(operationLabel, ex.getMessage)
      }
    }

  /** Code: 200, Message: EService retrieved, DataType: Agreement
    * Code: 404, Message: Agreement not found, DataType: Problem
    * Code: 400, Message: Bad request, DataType: Problem
    */
  override def getAgreement(agreementId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE) {

    val operationLabel: String = s"Getting agreement $agreementId"

    logger.info(operationLabel)

    val result: Future[PersistentAgreement] =
      commander(agreementId).askWithStatus(ref => GetAgreement(agreementId, ref))

    onComplete(result) {
      case Success(agreement)             =>
        getAgreement200(PersistentAgreement.toAPI(agreement))
      case Failure(ex: AgreementNotFound) =>
        logger.error(s"Error while $operationLabel", ex)
        getAgreement404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex)                    =>
        logger.error(s"Error while $operationLabel", ex)
        internalServerError(operationLabel, ex.getMessage)
    }
  }

  /** Code: 200, Message: A list of Agreement, DataType: Seq[Agreement]
    */
  override def getAgreements(
    producerId: Option[String],
    consumerId: Option[String],
    eserviceId: Option[String],
    descriptorId: Option[String],
    states: String,
    attributeId: Option[String]
  )(implicit
    toEntityMarshallerAgreementarray: ToEntityMarshaller[Seq[Agreement]],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE) {

    val resourceId: String =
      s"""
         |producer=${producerId.getOrElse("")}/
         |consumer=${consumerId.getOrElse("")}/
         |eservice=${eserviceId.getOrElse("")}/
         |descriptor=${descriptorId.getOrElse("")}/
         |attributeId=${attributeId.getOrElse("")}/
         |states=$states
         |""".stripMargin.replaceAll("\n", "")

    val operationLabel: String = s"Getting agreements with $resourceId"

    logger.info(operationLabel)

    val sliceSize = 100

    val commanders: Seq[EntityRef[Command]] =
      (0 until settings.numberOfShards).map(shard => commander(shard.toString))

    val result: Either[Throwable, Seq[PersistentAgreement]] = for {
      statesEnums <- parseArrayParameters(states).traverse(AgreementState.fromValue)
      generator  = createListAgreementsGenerator(
        producerId = producerId,
        consumerId = consumerId,
        eserviceId = eserviceId,
        descriptorId = descriptorId,
        attributeId = attributeId,
        states = statesEnums
      )(_, _)
      agreements = commanders.flatMap(ref => slices(ref, sliceSize)(generator))
    } yield agreements

    result match {
      case Right(agreements) => getAgreements200(agreements.map(PersistentAgreement.toAPI))
      case Left(error)       =>
        logger.error(s"Error while $operationLabel", error)
        getAgreements400(problemOf(StatusCodes.BadRequest, GenericError(operationLabel, error.getMessage)))
    }

  }

  private def createListAgreementsGenerator(
    producerId: Option[String],
    consumerId: Option[String],
    eserviceId: Option[String],
    descriptorId: Option[String],
    attributeId: Option[String],
    states: List[AgreementState]
  )(from: Int, to: Int): ActorRef[Seq[PersistentAgreement]] => ListAgreements =
    (ref: ActorRef[Seq[PersistentAgreement]]) =>
      ListAgreements(
        from,
        to,
        producerId,
        consumerId,
        eserviceId,
        descriptorId,
        attributeId,
        states.map(PersistentAgreementState.fromApi),
        ref
      )

  override def addAgreementConsumerDocument(agreementId: String, documentSeed: DocumentSeed)(implicit
    toEntityMarshallerAgreement: ToEntityMarshaller[Document],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {

    val operationLabel: String = s"Adding consumer document to agreement $agreementId"

    logger.info(operationLabel)

    val result: Future[PersistentAgreementDocument] =
      commander(agreementId).askWithStatus(ref =>
        AddAgreementConsumerDocument(
          agreementId,
          PersistentAgreementDocument.fromAPI(documentSeed)(UUIDSupplier, dateTimeSupplier),
          ref
        )
      )

    onComplete(result) {
      case Success(document)              =>
        addAgreementConsumerDocument200(PersistentAgreementDocument.toAPI(document))
      case Failure(ex: AgreementNotFound) =>
        logger.error(s"Error while $operationLabel", ex)
        addAgreementConsumerDocument404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex)                    =>
        logger.error(s"Error while $operationLabel", ex)
        internalServerError(operationLabel, ex.getMessage)
    }
  }

  override def removeAgreementConsumerDocument(agreementId: String, documentId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {

    val operationLabel: String = s"Removing consumer document $documentId from agreement $agreementId"

    logger.info(operationLabel)

    val result: Future[Unit] =
      commander(agreementId).askWithStatus(ref => RemoveAgreementConsumerDocument(agreementId, documentId, ref))

    onComplete(result) {
      case Success(_)                     =>
        removeAgreementConsumerDocument204
      case Failure(ex: AgreementNotFound) =>
        logger.error(s"Error while $operationLabel", ex)
        removeAgreementConsumerDocument404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex)                    =>
        logger.error(s"Error while $operationLabel", ex)
        internalServerError(operationLabel, ex.getMessage)
    }
  }

  override def getAgreementConsumerDocument(agreementId: String, documentId: String)(implicit
    toEntityMarshallerDocument: ToEntityMarshaller[Document],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {

    val operationLabel: String = s"Retrieving consumer document $documentId from agreement $agreementId"

    logger.info(operationLabel)

    val result: Future[PersistentAgreementDocument] =
      commander(agreementId).askWithStatus(ref => GetAgreementConsumerDocument(agreementId, documentId, ref))

    onComplete(result) {
      case Success(document)                      =>
        getAgreementConsumerDocument200(PersistentAgreementDocument.toAPI(document))
      case Failure(ex: AgreementNotFound)         =>
        logger.error(s"Error while $operationLabel", ex)
        getAgreementConsumerDocument404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex: AgreementDocumentNotFound) =>
        logger.error(s"Error while $operationLabel", ex)
        getAgreementConsumerDocument404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex)                            =>
        logger.error(s"Error while $operationLabel", ex)
        internalServerError(operationLabel, ex.getMessage)
    }
  }

  // TODO introduce proper uuid handling (e.g.: Twitter snowflake)
  override def upgradeAgreementById(agreementId: String, seed: UpgradeAgreementSeed)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {

    val operationLabel: String = s"Upgrading agreement $agreementId"

    logger.info(s"$operationLabel, with data $seed")

    val result: Future[PersistentAgreement] = for {
      oldAgreement <- commander(agreementId).askWithStatus(ref => GetAgreement(agreementId, ref))
      deactivated = PersistentAgreement.update(
        oldAgreement,
        UpdateAgreementSeed(
          state = ARCHIVED,
          certifiedAttributes = oldAgreement.certifiedAttributes.map(PersistentCertifiedAttribute.toAPI),
          declaredAttributes = oldAgreement.declaredAttributes.map(PersistentDeclaredAttribute.toAPI),
          verifiedAttributes = oldAgreement.verifiedAttributes.map(PersistentVerifiedAttribute.toAPI),
          suspendedByConsumer = oldAgreement.suspendedByConsumer,
          suspendedByProducer = oldAgreement.suspendedByProducer,
          suspendedByPlatform = oldAgreement.suspendedByPlatform
        ),
        dateTimeSupplier
      )
      _ <- commander(agreementId).askWithStatus(ref => UpdateAgreement(deactivated, ref))
      persistentAgreement = PersistentAgreement.upgrade(oldAgreement, seed)(UUIDSupplier, dateTimeSupplier)
      activeAgreement <- commander(persistentAgreement.id.toString).askWithStatus(ref =>
        AddAgreement(persistentAgreement, ref)
      )
    } yield activeAgreement

    onComplete(result) {
      case Success(agreement)                       =>
        upgradeAgreementById200(PersistentAgreement.toAPI(agreement))
      case Failure(ex: AgreementNotFound)           =>
        logger.error(s"Error while $operationLabel, with data $seed", ex)
        upgradeAgreementById404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex: AgreementNotInExpectedState) =>
        logger.error(s"Error while $operationLabel, with data $seed", ex)
        upgradeAgreementById400(problemOf(StatusCodes.BadRequest, ex))
      case Failure(ex)                              =>
        logger.error(s"Error while $operationLabel, with data $seed", ex)
        internalServerError(operationLabel, ex.getMessage)
    }
  }

  private def commander(id: String): EntityRef[Command] =
    sharding.entityRefFor(AgreementPersistentBehavior.TypeKey, getShard(id))

  private def internalServerError(operationLabel: String, errorMessage: String): StandardRoute =
    complete(StatusCodes.InternalServerError, GenericError(operationLabel, errorMessage))
}
