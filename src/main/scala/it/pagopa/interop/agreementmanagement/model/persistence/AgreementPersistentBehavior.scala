package it.pagopa.interop.agreementmanagement.model.persistence

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EffectBuilder, EventSourcedBehavior, RetentionCriteria}
import it.pagopa.interop.agreementmanagement.error.AgreementManagementErrors.{AgreementConflict, AgreementNotFound}
import it.pagopa.interop.agreementmanagement.model.agreement._
import it.pagopa.interop.agreementmanagement.model.persistence.Adapters._
import it.pagopa.interop.agreementmanagement.model.{ChangedBy, StateChangeDetails}
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier

import java.time.temporal.ChronoUnit
import scala.concurrent.duration.{DurationInt, DurationLong}
import scala.language.postfixOps
import java.time.Duration

object AgreementPersistentBehavior {

  def commandHandler(
    shard: ActorRef[ClusterSharding.ShardCommand],
    context: ActorContext[Command],
    dateTimeSupplier: OffsetDateTimeSupplier
  ): (State, Command) => Effect[Event, State] = { (state, command) =>
    val idleTimeout: Duration = context.system.settings.config.getDuration("agreement-management.idle-timeout")
    context.setReceiveTimeout(idleTimeout.get(ChronoUnit.SECONDS) seconds, Idle)

    command match {
      case AddAgreement(newAgreement, replyTo) =>
        val agreement: Either[Throwable, PersistentAgreement] = state.agreements
          .get(newAgreement.id.toString)
          .map(found => AgreementConflict(found.id.toString))
          .toLeft(newAgreement)

        agreement
          .fold(handleFailure[AgreementAdded](_)(replyTo), persistStateAndReply(_, AgreementAdded)(replyTo))

      case UpdateVerifiedAttribute(agreementId, updateVerifiedAttribute, replyTo) =>
        val attributeId = updateVerifiedAttribute.id.toString

        val agreementUpdated: Either[AgreementNotFound, PersistentAgreement] = for {
          agreement <- state
            .getAgreementContainingVerifiedAttribute(agreementId, attributeId)
            .toRight(AgreementNotFound(agreementId))
        } yield state.updateAgreementContent(
          agreement,
          PersistentVerifiedAttribute.fromAPI(dateTimeSupplier)(updateVerifiedAttribute)
        )

        agreementUpdated.fold(
          handleFailure[VerifiedAttributeUpdated](_)(replyTo),
          persistStateAndReply(_, VerifiedAttributeUpdated)(replyTo)
        )

      case GetAgreement(agreementId, replyTo) =>
        val agreement: Either[AgreementNotFound, PersistentAgreement] =
          state.agreements.get(agreementId).toRight(AgreementNotFound(agreementId))
        agreement.fold(
          ex => {
            replyTo ! StatusReply.Error[PersistentAgreement](ex)
            Effect.none[Event, State]
          },
          agreement => {
            replyTo ! StatusReply.Success[PersistentAgreement](agreement)
            Effect.none[Event, State]
          }
        )

      case ActivateAgreement(agreementId, stateChangeDetails, replyTo) =>
        val agreement: Either[Throwable, PersistentAgreement] =
          getModifiedAgreement(state, agreementId, stateChangeDetails, Active, _.isActivable)(dateTimeSupplier)

        agreement
          .fold(handleFailure[AgreementActivated](_)(replyTo), persistStateAndReply(_, AgreementActivated)(replyTo))

      case SuspendAgreement(agreementId, stateChangeDetails, replyTo) =>
        val agreement: Either[Throwable, PersistentAgreement] =
          getModifiedAgreement(state, agreementId, stateChangeDetails, Suspended, _.isSuspendable)(dateTimeSupplier)

        agreement
          .fold(handleFailure[AgreementSuspended](_)(replyTo), persistStateAndReply(_, AgreementSuspended)(replyTo))

      case DeactivateAgreement(agreementId, stateChangeDetails, replyTo) =>
        val agreement: Either[Throwable, PersistentAgreement] =
          getModifiedAgreement(state, agreementId, stateChangeDetails, Inactive, _.isDeactivable)(dateTimeSupplier)

        agreement
          .fold(handleFailure[AgreementDeactivated](_)(replyTo), persistStateAndReply(_, AgreementDeactivated)(replyTo))

      case ListAgreements(from, to, producerId, consumerId, eserviceId, descriptorId, agreementState, replyTo) =>
        val agreements: Seq[PersistentAgreement] = state.agreements
          .slice(from, to)
          .filter(agreement => producerId.forall(filter => filter == agreement._2.producerId.toString))
          .filter(agreement => consumerId.forall(filter => filter == agreement._2.consumerId.toString))
          .filter(agreement => eserviceId.forall(filter => filter == agreement._2.eserviceId.toString))
          .filter(agreement => descriptorId.forall(filter => filter == agreement._2.descriptorId.toString))
          .filter(agreement => agreementState.forall(filter => filter == agreement._2.state))
          .values
          .toSeq

        replyTo ! agreements
        Effect.none[Event, State]

      case Idle =>
        shard ! ClusterSharding.Passivate(context.self)
        context.log.debug(s"Passivated shard: ${shard.path.name}")
        Effect.none[Event, State]
    }
  }

  def handleFailure[T](ex: Throwable)(replyTo: ActorRef[StatusReply[PersistentAgreement]]): EffectBuilder[T, State] = {
    replyTo ! StatusReply.Error[PersistentAgreement](ex)
    Effect.none[T, State]
  }

  def persistStateAndReply[T](purpose: PersistentAgreement, eventBuilder: PersistentAgreement => T)(
    replyTo: ActorRef[StatusReply[PersistentAgreement]]
  ): EffectBuilder[T, State] =
    Effect.persist(eventBuilder(purpose)).thenRun((_: State) => replyTo ! StatusReply.Success(purpose))

  val eventHandler: (State, Event) => State = (state, event) =>
    event match {
      case AgreementAdded(agreement)           => state.add(agreement)
      case AgreementActivated(agreement)       => state.updateAgreement(agreement)
      case AgreementSuspended(agreement)       => state.updateAgreement(agreement)
      case AgreementDeactivated(agreement)     => state.updateAgreement(agreement)
      case VerifiedAttributeUpdated(agreement) => state.updateAgreement(agreement)
    }

  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("interop-be-agreement-management-persistence")

  def apply(
    shard: ActorRef[ClusterSharding.ShardCommand],
    persistenceId: PersistenceId,
    dateTimeSupplier: OffsetDateTimeSupplier,
    projectionTag: String
  ): Behavior[Command] = Behaviors.setup { context =>
    context.log.debug(s"Starting Agreement Shard ${persistenceId.id}")
    val numberOfEvents =
      context.system.settings.config
        .getInt("agreement-management.number-of-events-before-snapshot")
    EventSourcedBehavior[Command, Event, State](
      persistenceId = persistenceId,
      emptyState = State.empty,
      commandHandler = commandHandler(shard, context, dateTimeSupplier),
      eventHandler = eventHandler
    ).withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = numberOfEvents, keepNSnapshots = 1))
      .withTagger(_ => Set(projectionTag))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200 millis, 5 seconds, 0.1))
  }

  private def updateAgreementState(
    persistentAgreement: PersistentAgreement,
    state: PersistentAgreementState,
    stateChangeDetails: StateChangeDetails
  )(dateTimeSupplier: OffsetDateTimeSupplier): PersistentAgreement = {

    val timestamp   = Some(dateTimeSupplier.get)
    def isSuspended = state == Suspended

    stateChangeDetails.changedBy match {
      case Some(changedBy) =>
        changedBy match {
          case ChangedBy.CONSUMER =>
            val newState = calcNewAgreementState(
              suspendedByProducer = persistentAgreement.suspendedByProducer,
              suspendedByConsumer = Some(isSuspended),
              newState = state
            )
            persistentAgreement.copy(state = newState, suspendedByConsumer = Some(isSuspended), updatedAt = timestamp)
          case ChangedBy.PRODUCER =>
            val newState = calcNewAgreementState(
              suspendedByProducer = Some(isSuspended),
              suspendedByConsumer = persistentAgreement.suspendedByConsumer,
              newState = state
            )
            persistentAgreement.copy(state = newState, suspendedByProducer = Some(isSuspended), updatedAt = timestamp)
        }
      case None            => persistentAgreement.copy(state = state, updatedAt = timestamp)
    }

  }
  def calcNewAgreementState(
    suspendedByProducer: Option[Boolean],
    suspendedByConsumer: Option[Boolean],
    newState: PersistentAgreementState
  ): PersistentAgreementState = (newState, suspendedByProducer, suspendedByConsumer) match {
    case (Active, Some(true), _) => Suspended
    case (Active, _, Some(true)) => Suspended
    case _                       => newState
  }

  def getModifiedAgreement(
    state: State,
    agreementId: String,
    stateChangeDetails: StateChangeDetails,
    newState: PersistentAgreementState,
    agreementValidation: PersistentAgreement => Either[Throwable, Unit]
  )(dateTimeSupplier: OffsetDateTimeSupplier): Either[Throwable, PersistentAgreement] = for {
    agreement <- state.agreements.get(agreementId).toRight(AgreementNotFound(agreementId))
    _         <- agreementValidation(agreement)
  } yield updateAgreementState(agreement, newState, stateChangeDetails)(dateTimeSupplier)

}
