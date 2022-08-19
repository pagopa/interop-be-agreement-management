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
import it.pagopa.interop.agreementmanagement.model._
import it.pagopa.interop.agreementmanagement.model.agreement.{
  PersistentAgreement,
  PersistentAgreementState,
  PersistentAgreementDocument
}
import it.pagopa.interop.agreementmanagement.model.persistence.Adapters._
import it.pagopa.interop.agreementmanagement.model.persistence._
import it.pagopa.interop.commons.jwt._
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
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

  /** Code: 200, Message: Agreement created, DataType: Agreement
    * Code: 405, Message: Invalid input, DataType: Problem
    */
  override def addAgreement(agreementSeed: AgreementSeed)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {

    val operationLabel: String = "Adding an agreement"

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
          case ex                    => internalServerError(operationLabel, agreement.id.toString, ex.getMessage)
        }
      case Failure(ex)                                   =>
        logger.error(
          s"Error while $operationLabel  for consumer ${agreementSeed.consumerId} to descriptor ${agreementSeed.descriptorId} " +
            s"of e-service ${agreementSeed.eserviceId} from the producer ${agreementSeed.producerId} ",
          ex
        )
        internalServerError(operationLabel, agreement.id.toString, ex.getMessage)
    }
  }

  private def createAgreement(agreement: PersistentAgreement) = {
    val commander: EntityRef[Command] =
      sharding.entityRefFor(AgreementPersistentBehavior.TypeKey, getShard(agreement.id.toString))
    commander.ask(ref => AddAgreement(agreement, ref))
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

    val operationLabel: String = "Getting agreement"

    logger.info(s"$operationLabel $agreementId")

    val commander: EntityRef[Command] =
      sharding.entityRefFor(AgreementPersistentBehavior.TypeKey, getShard(agreementId))

    val result: Future[StatusReply[PersistentAgreement]] = commander.ask(ref => GetAgreement(agreementId, ref))

    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        getAgreement200(PersistentAgreement.toAPI(statusReply.getValue))
      case Success(statusReply)                          =>
        logger.error(s"Error while $operationLabel $agreementId", statusReply.getError)
        statusReply.getError match {
          case ex: AgreementNotFound => getAgreement404(problemOf(StatusCodes.NotFound, ex))
          case ex                    => internalServerError(operationLabel, agreementId, ex.getMessage)
        }
      case Failure(ex)                                   =>
        logger.error(s"Error while $operationLabel $agreementId", ex)
        internalServerError(operationLabel, agreementId, ex.getMessage)
    }
  }

  override def activateAgreement(agreementId: String, stateChangeDetails: StateChangeDetails)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {
    val operationLabel: String = "Activating agreement"

    logger.info(s"$operationLabel $agreementId")

    val result: Future[StatusReply[PersistentAgreement]] = activateAgreementById(agreementId, stateChangeDetails)
    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        activateAgreement200(PersistentAgreement.toAPI(statusReply.getValue))
      case Success(statusReply)                          =>
        logger.error(s"Error while $operationLabel $agreementId", statusReply.getError)
        statusReply.getError match {
          case ex: AgreementNotFound           => activateAgreement404(problemOf(StatusCodes.NotFound, ex))
          case ex: AgreementNotInExpectedState => activateAgreement400(problemOf(StatusCodes.BadRequest, ex))
          case ex                              => internalServerError(operationLabel, agreementId, ex.getMessage)
        }
      case Failure(ex)                                   =>
        logger.error(s"Error while $operationLabel $agreementId", ex)
        internalServerError(operationLabel, agreementId, ex.getMessage)
    }
  }

  private def activateAgreementById(agreementId: String, stateChangeDetails: StateChangeDetails) = {
    val commander: EntityRef[Command] =
      sharding.entityRefFor(AgreementPersistentBehavior.TypeKey, getShard(agreementId))

    commander.ask(ref => ActivateAgreement(agreementId, stateChangeDetails, ref))
  }

  override def suspendAgreement(agreementId: String, stateChangeDetails: StateChangeDetails)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {

    val operationLabel: String = "Suspending agreement"

    logger.info(s"$operationLabel $agreementId")

    val result: Future[StatusReply[PersistentAgreement]] = suspendAgreementById(agreementId, stateChangeDetails)

    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        suspendAgreement200(PersistentAgreement.toAPI(statusReply.getValue))
      case Success(statusReply)                          =>
        logger.error(s"Error while $operationLabel $agreementId", statusReply.getError)
        statusReply.getError match {
          case ex: AgreementNotFound           => suspendAgreement404(problemOf(StatusCodes.NotFound, ex))
          case ex: AgreementNotInExpectedState => suspendAgreement400(problemOf(StatusCodes.BadRequest, ex))
          case ex                              => internalServerError(operationLabel, agreementId, ex.getMessage)
        }
      case Failure(ex)                                   =>
        logger.error(s"Error while $operationLabel $agreementId", ex)
        internalServerError(operationLabel, agreementId, ex.getMessage)
    }
  }

  private def suspendAgreementById(agreementId: String, stateChangeDetails: StateChangeDetails) = {
    val commander: EntityRef[Command] =
      sharding.entityRefFor(AgreementPersistentBehavior.TypeKey, getShard(agreementId))

    commander.ask(ref => SuspendAgreement(agreementId, stateChangeDetails, ref))
  }

  /** Code: 200, Message: A list of Agreement, DataType: Seq[Agreement]
    */
  override def getAgreements(
    producerId: Option[String],
    consumerId: Option[String],
    eserviceId: Option[String],
    descriptorId: Option[String],
    state: Option[String]
  )(implicit
    toEntityMarshallerAgreementarray: ToEntityMarshaller[Seq[Agreement]],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE) {

    val operationLabel: String = "Getting agreements for"

    logger.info(
      s"$operationLabel consumer $consumerId to e-service $eserviceId of the producer $producerId " +
        s"with the descriptor $descriptorId and state $state"
    )

    val sliceSize = 100

    val commanders: Seq[EntityRef[Command]] = (0 until settings.numberOfShards).map(shard =>
      sharding.entityRefFor(AgreementPersistentBehavior.TypeKey, shard.toString)
    )

    val result: Either[Throwable, Seq[PersistentAgreement]] = for {
      stateEnum <- state.traverse(AgreementState.fromValue)
      generator  = createListAgreementsGenerator(
        producerId = producerId,
        consumerId = consumerId,
        eserviceId = eserviceId,
        descriptorId = descriptorId,
        state = stateEnum
      )(_, _)
      agreements = commanders.flatMap(ref => slices(ref, sliceSize)(generator))
    } yield agreements

    result match {
      case Right(agreements) => getAgreements200(agreements.map(PersistentAgreement.toAPI))
      case Left(error)       =>
        logger.error(
          s"Error while $operationLabel consumer $consumerId to e-service $eserviceId of the producer $producerId " +
            s"with the descriptor $descriptorId and state $state",
          error
        )
        val resourceId: String =
          s"""
             |producer=${producerId.getOrElse("")}/
             |consumer=${consumerId.getOrElse("")}/
             |eservice=${eserviceId.getOrElse("")}/
             |descriptor=${descriptorId.getOrElse("")}/
             |state=${state.getOrElse("")}
             |""".stripMargin.replaceAll("\n", "")
        getAgreements400(problemOf(StatusCodes.BadRequest, GenericError(operationLabel, resourceId, error.getMessage)))
    }

  }

  private def createListAgreementsGenerator(
    producerId: Option[String],
    consumerId: Option[String],
    eserviceId: Option[String],
    descriptorId: Option[String],
    state: Option[AgreementState]
  )(from: Int, to: Int): ActorRef[Seq[PersistentAgreement]] => ListAgreements =
    (ref: ActorRef[Seq[PersistentAgreement]]) =>
      ListAgreements(
        from,
        to,
        producerId,
        consumerId,
        eserviceId,
        descriptorId,
        state.map(PersistentAgreementState.fromApi),
        ref
      )

  override def addAgreementConsumerDocument(agreementId: String, documentSeed: DocumentSeed)(implicit
    toEntityMarshallerAgreement: ToEntityMarshaller[Document],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {

    val operationLabel: String = s"Adding consumer document to agreement $agreementId"

    logger.info(operationLabel)

    val commander: EntityRef[Command] =
      sharding.entityRefFor(AgreementPersistentBehavior.TypeKey, getShard(agreementId))

    val result: Future[PersistentAgreementDocument] =
      commander.askWithStatus(ref =>
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
        internalServerError(operationLabel, agreementId, ex.getMessage)
    }
  }

  override def removeAgreementConsumerDocument(agreementId: String, documentId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {

    val operationLabel: String = s"Removing consumer document $documentId from agreement $agreementId"

    logger.info(operationLabel)

    val commander: EntityRef[Command] =
      sharding.entityRefFor(AgreementPersistentBehavior.TypeKey, getShard(agreementId))

    val result: Future[Unit] =
      commander.askWithStatus(ref => RemoveAgreementConsumerDocument(agreementId, documentId, ref))

    onComplete(result) {
      case Success(_)                     =>
        removeAgreementConsumerDocument204
      case Failure(ex: AgreementNotFound) =>
        logger.error(s"Error while $operationLabel", ex)
        removeAgreementConsumerDocument404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex)                    =>
        logger.error(s"Error while $operationLabel", ex)
        internalServerError(operationLabel, agreementId, ex.getMessage)
    }
  }

  override def getAgreementConsumerDocument(agreementId: String, documentId: String)(implicit
    toEntityMarshallerDocument: ToEntityMarshaller[Document],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {

    val operationLabel: String = s"Retrieving consumer document $documentId from agreement $agreementId"

    logger.info(operationLabel)

    val commander: EntityRef[Command] =
      sharding.entityRefFor(AgreementPersistentBehavior.TypeKey, getShard(agreementId))

    val result: Future[PersistentAgreementDocument] =
      commander.askWithStatus(ref => GetAgreementConsumerDocument(agreementId, documentId, ref))

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
        internalServerError(operationLabel, agreementId, ex.getMessage)
    }
  }

  // TODO introduce proper uuid handling (e.g.: Twitter snowflake)
  override def upgradeAgreementById(agreementId: String, seed: UpgradeAgreementSeed)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {

    val operationLabel: String = "Upgrading agreement"

    logger.info(s"$operationLabel $agreementId, with data $seed")

    val result = for {
      oldAgreement <- deactivateAgreementById(agreementId, StateChangeDetails(changedBy = None))
      persistentAgreement = PersistentAgreement.upgrade(oldAgreement, seed)(UUIDSupplier, dateTimeSupplier)
      activeAgreement <- createAgreement(persistentAgreement)
    } yield activeAgreement

    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        upgradeAgreementById200(PersistentAgreement.toAPI(statusReply.getValue))
      case Success(statusReply)                          =>
        logger.error(s"Error while $operationLabel $agreementId, with data $seed", statusReply.getError)
        statusReply.getError match {
          case ex: AgreementNotFound           => upgradeAgreementById404(problemOf(StatusCodes.NotFound, ex))
          case ex: AgreementNotInExpectedState => upgradeAgreementById400(problemOf(StatusCodes.BadRequest, ex))
          case ex                              => internalServerError(operationLabel, agreementId, ex.getMessage)
        }
      case Failure(ex)                                   =>
        logger.error(s"Error while $operationLabel $agreementId, with data $seed", ex)
        internalServerError(operationLabel, agreementId, ex.getMessage)
    }
  }

  private def deactivateAgreementById(
    agreementId: String,
    stateChangeDetails: StateChangeDetails
  ): Future[PersistentAgreement] = {
    val commander: EntityRef[Command] =
      sharding.entityRefFor(AgreementPersistentBehavior.TypeKey, getShard(agreementId))

    commander.askWithStatus(ref => DeactivateAgreement(agreementId, stateChangeDetails, ref))
  }

  private def internalServerError(operationLabel: String, resourceId: String, errorMessage: String): StandardRoute =
    complete(StatusCodes.InternalServerError, GenericError(operationLabel, resourceId, errorMessage))
}
