package it.pagopa.interop.agreementmanagement.api.impl

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.{Route, StandardRoute}
import akka.pattern.StatusReply
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementmanagement.api.AgreementApiService
import it.pagopa.interop.agreementmanagement.common.system._
import it.pagopa.interop.agreementmanagement.error.AgreementManagementErrors._
import it.pagopa.interop.agreementmanagement.model._
import it.pagopa.interop.agreementmanagement.model.agreement.{PersistentAgreement, PersistentAgreementState}
import it.pagopa.interop.agreementmanagement.model.persistence._
import it.pagopa.interop.agreementmanagement.model.persistence.Adapters._
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import org.slf4j.LoggerFactory

import scala.concurrent._
import scala.util.{Failure, Success}

final case class AgreementApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]],
  UUIDSupplier: UUIDSupplier,
  dateTimeSupplier: OffsetDateTimeSupplier
)(implicit ec: ExecutionContext)
    extends AgreementApiService {

  val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](LoggerFactory.getLogger(this.getClass))

  private val settings: ClusterShardingSettings = entity.settings match {
    case None    => ClusterShardingSettings(system)
    case Some(s) => s
  }

  @inline private def getShard(id: String): String = Math.abs(id.hashCode % settings.numberOfShards).toString

  /** Code: 200, Message: Agreement created, DataType: Agreement
    * Code: 405, Message: Invalid input, DataType: Problem
    */
  override def addAgreement(agreementSeed: AgreementSeed)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement],
    contexts: Seq[(String, String)]
  ): Route = {

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
            s"of e-service ${agreementSeed.eserviceId} from the producer ${agreementSeed.producerId} " +
            s"- ${statusReply.getError.getMessage}"
        )
        statusReply.getError match {
          case ex: AgreementConflict => addAgreement409(problemOf(StatusCodes.Conflict, ex))
          case ex                    => internalServerError(operationLabel, agreement.id.toString, ex.getMessage)
        }
      case Failure(ex)                                   =>
        logger.error(
          s"Error while $operationLabel  for consumer ${agreementSeed.consumerId} to descriptor ${agreementSeed.descriptorId} " +
            s"of e-service ${agreementSeed.eserviceId} from the producer ${agreementSeed.producerId} " +
            s"- ${ex.getMessage}"
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
  ): Route = {

    val operationLabel: String = "Getting agreement"

    logger.info(s"$operationLabel $agreementId")

    val commander: EntityRef[Command] =
      sharding.entityRefFor(AgreementPersistentBehavior.TypeKey, getShard(agreementId))

    val result: Future[StatusReply[PersistentAgreement]] = commander.ask(ref => GetAgreement(agreementId, ref))

    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        getAgreement200(PersistentAgreement.toAPI(statusReply.getValue))
      case Success(statusReply)                          =>
        logger.error(s"Error while $operationLabel $agreementId - ${statusReply.getError.getMessage}")
        statusReply.getError match {
          case ex: AgreementNotFound => getAgreement404(problemOf(StatusCodes.NotFound, ex))
          case ex                    => internalServerError(operationLabel, agreementId, ex.getMessage)
        }
      case Failure(ex)                                   =>
        logger.error(s"Error while $operationLabel $agreementId - ${ex.getMessage}")
        internalServerError(operationLabel, agreementId, ex.getMessage)
    }
  }

  override def activateAgreement(agreementId: String, stateChangeDetails: StateChangeDetails)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel: String = "Activating agreement"

    logger.info(s"$operationLabel $agreementId")

    val result: Future[StatusReply[PersistentAgreement]] = activateAgreementById(agreementId, stateChangeDetails)
    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        activateAgreement200(PersistentAgreement.toAPI(statusReply.getValue))
      case Success(statusReply)                          =>
        logger.error(s"Error while $operationLabel $agreementId - ${statusReply.getError.getMessage}")
        statusReply.getError match {
          case ex: AgreementNotFound           => activateAgreement404(problemOf(StatusCodes.NotFound, ex))
          case ex: AgreementNotInExpectedState => activateAgreement400(problemOf(StatusCodes.BadRequest, ex))
          case ex                              => internalServerError(operationLabel, agreementId, ex.getMessage)
        }
      case Failure(ex)                                   =>
        logger.error(s"Error while $operationLabel $agreementId - ${ex.getMessage}")
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
  ): Route = {

    val operationLabel: String = "Suspending agreement"

    logger.info(s"$operationLabel $agreementId")

    val result: Future[StatusReply[PersistentAgreement]] = suspendAgreementById(agreementId, stateChangeDetails)

    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        suspendAgreement200(PersistentAgreement.toAPI(statusReply.getValue))
      case Success(statusReply)                          =>
        logger.error(s"Error while $operationLabel $agreementId - ${statusReply.getError.getMessage}")
        statusReply.getError match {
          case ex: AgreementNotFound           => suspendAgreement404(problemOf(StatusCodes.NotFound, ex))
          case ex: AgreementNotInExpectedState => suspendAgreement400(problemOf(StatusCodes.BadRequest, ex))
          case ex                              => internalServerError(operationLabel, agreementId, ex.getMessage)
        }
      case Failure(ex)                                   =>
        logger.error(s"Error while $operationLabel $agreementId - ${ex.getMessage}")
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
  ): Route = {

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
            s"with the descriptor $descriptorId and state $state - ${error.getMessage}"
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

  /** Code: 200, Message: Returns the agreement with the updated attribute state., DataType: Agreement
    * Code: 400, Message: Bad Request, DataType: Problem
    * Code: 404, Message: Resource Not Found, DataType: Problem
    */
  override def updateAgreementVerifiedAttribute(agreementId: String, verifiedAttributeSeed: VerifiedAttributeSeed)(
    implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement],
    contexts: Seq[(String, String)]
  ): Route = {

    val operationLabel: String = "Updating agreement"

    logger.info(s"$operationLabel $agreementId verified attribute ${verifiedAttributeSeed.id}")

    val commander: EntityRef[Command] =
      sharding.entityRefFor(AgreementPersistentBehavior.TypeKey, getShard(agreementId))

    val result: Future[StatusReply[PersistentAgreement]] =
      commander.ask(ref => UpdateVerifiedAttribute(agreementId, verifiedAttributeSeed, ref))

    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        updateAgreementVerifiedAttribute200(PersistentAgreement.toAPI(statusReply.getValue))
      case Success(statusReply)                          =>
        logger.error(
          s"Error while $operationLabel $agreementId verified attribute ${verifiedAttributeSeed.id} " +
            s"- ${statusReply.getError.getMessage}"
        )
        statusReply.getError match {
          case ex: AgreementNotFound => updateAgreementVerifiedAttribute404(problemOf(StatusCodes.NotFound, ex))
          case ex                    => internalServerError(operationLabel, agreementId, ex.getMessage)
        }
      case Failure(ex)                                   =>
        logger.error(
          s"Error while $operationLabel $agreementId verified attribute ${verifiedAttributeSeed.id} " +
            s"- ${ex.getMessage}"
        )
        internalServerError(operationLabel, agreementId, ex.getMessage)
    }
  }

  // TODO introduce proper uuid handling (e.g.: Twitter snowflake)
  override def upgradeAgreementById(agreementId: String, agreementSeed: AgreementSeed)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement],
    contexts: Seq[(String, String)]
  ): Route = {

    val operationLabel: String = "Updating agreement"

    logger.info(s"$operationLabel $agreementId, with data $agreementSeed")

    val result = for {
      _ <- deactivateAgreementById(agreementId, StateChangeDetails(changedBy = None))
      persistentAgreement = PersistentAgreement.fromAPIWithActiveState(agreementSeed, UUIDSupplier, dateTimeSupplier)
      activeAgreement <- createAgreement(persistentAgreement)
    } yield activeAgreement

    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        upgradeAgreementById200(PersistentAgreement.toAPI(statusReply.getValue))
      case Success(statusReply)                          =>
        logger.error(
          s"Error while $operationLabel $agreementId, with data $agreementSeed - ${statusReply.getError.getMessage}"
        )
        statusReply.getError match {
          case ex: AgreementNotFound           => upgradeAgreementById404(problemOf(StatusCodes.NotFound, ex))
          case ex: AgreementNotInExpectedState => upgradeAgreementById400(problemOf(StatusCodes.BadRequest, ex))
          case ex                              => internalServerError(operationLabel, agreementId, ex.getMessage)
        }
      case Failure(ex)                                   =>
        logger.error(s"Error while $operationLabel $agreementId, with data $agreementSeed - ${ex.getMessage}")
        internalServerError(operationLabel, agreementId, ex.getMessage)
    }
  }

  private def deactivateAgreementById(agreementId: String, stateChangeDetails: StateChangeDetails) = {
    val commander: EntityRef[Command] =
      sharding.entityRefFor(AgreementPersistentBehavior.TypeKey, getShard(agreementId))

    commander.ask(ref => DeactivateAgreement(agreementId, stateChangeDetails, ref))
  }

  private def internalServerError(operationLabel: String, resourceId: String, errorMessage: String): StandardRoute =
    complete(StatusCodes.InternalServerError, GenericError(operationLabel, resourceId, errorMessage))
}
