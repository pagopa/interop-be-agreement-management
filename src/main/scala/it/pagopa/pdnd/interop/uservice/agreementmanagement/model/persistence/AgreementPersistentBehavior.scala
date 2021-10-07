package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.{Agreement, StatusChangeDetails}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.agreement.{
  PersistentAgreement,
  PersistentAgreementStatus,
  PersistentVerifiedAttribute,
  StatusChangeDetailsEnum
}

import java.time.temporal.ChronoUnit
import scala.concurrent.duration.{DurationInt, DurationLong}
import scala.language.postfixOps

@SuppressWarnings(Array("org.wartremover.warts.Equals"))
object AgreementPersistentBehavior {

  def commandHandler(
    shard: ActorRef[ClusterSharding.ShardCommand],
    context: ActorContext[Command]
  ): (State, Command) => Effect[Event, State] = { (state, command) =>
    val idleTimeout =
      context.system.settings.config.getDuration("uservice-agreement-management.idle-timeout")
    context.setReceiveTimeout(idleTimeout.get(ChronoUnit.SECONDS) seconds, Idle)
    command match {
      case AddAgreement(newAgreement, replyTo) =>
        val agreement: Option[PersistentAgreement] = state.agreements.get(newAgreement.id.toString)
        agreement
          .map { es =>
            replyTo ! StatusReply.Error[Agreement](s"Agreement ${es.id.toString} already exists")
            Effect.none[AgreementAdded, State]
          }
          .getOrElse {
            Effect
              .persist(AgreementAdded(newAgreement))
              .thenRun((_: State) => replyTo ! StatusReply.Success(PersistentAgreement.toAPI(newAgreement)))
          }

      case UpdateVerifiedAttribute(agreementId, updateVerifiedAttribute, replyTo) =>
        val attributeId = updateVerifiedAttribute.id.toString
        val agreementOpt: Option[PersistentAgreement] =
          state.getAgreementContainingVerifiedAttribute(agreementId, attributeId)
        agreementOpt
          .map { agreement =>
            val updatedAgreement =
              state.updateAgreementContent(agreement, PersistentVerifiedAttribute.fromAPI(updateVerifiedAttribute))
            Effect
              .persist(VerifiedAttributeUpdated(updatedAgreement))
              .thenRun((_: State) => replyTo ! StatusReply.Success(PersistentAgreement.toAPI(updatedAgreement)))
          }
          .getOrElse {
            replyTo ! StatusReply.Error[Agreement](s"Attribute ${attributeId} not found for agreement ${agreementId}.")
            Effect.none[VerifiedAttributeUpdated, State]
          }

      case GetAgreement(agreementId, replyTo) =>
        val agreement: Option[PersistentAgreement] = state.agreements.get(agreementId)
        replyTo ! StatusReply.Success[Option[Agreement]](agreement.map(PersistentAgreement.toAPI))
        Effect.none[Event, State]

      case ActivateAgreement(agreementId, statusChangeDetails, replyTo) =>
        val agreement: Option[PersistentAgreement] = state.agreements.get(agreementId)
        agreement
          .map { agreement =>
            val updatedAgreement =
              updateAgreementStatus(agreement, PersistentAgreementStatus.Active, statusChangeDetails)
            Effect
              .persist(AgreementActivated(updatedAgreement))
              .thenRun((_: State) => replyTo ! StatusReply.Success(PersistentAgreement.toAPI(updatedAgreement)))
          }
          .getOrElse {
            replyTo ! StatusReply.Error[Agreement](s"Agreement ${agreementId} not found.")
            Effect.none[AgreementActivated, State]
          }

      case SuspendAgreement(agreementId, statusChangeDetails, replyTo) =>
        val agreement: Option[PersistentAgreement] = state.agreements.get(agreementId)
        agreement
          .map { agreement =>
            val updatedAgreement =
              updateAgreementStatus(agreement, PersistentAgreementStatus.Suspended, statusChangeDetails)
            Effect
              .persist(AgreementSuspended(updatedAgreement))
              .thenRun((_: State) => replyTo ! StatusReply.Success(PersistentAgreement.toAPI(updatedAgreement)))
          }
          .getOrElse {
            replyTo ! StatusReply.Error[Agreement](s"Agreement ${agreementId} not found.")
            Effect.none[AgreementSuspended, State]
          }

      case DeactivateAgreement(agreementId, statusChangeDetails, replyTo) =>
        val agreement: Option[PersistentAgreement] = state.agreements.get(agreementId)
        agreement
          .map { agreement =>
            val updatedAgreement =
              updateAgreementStatus(agreement, PersistentAgreementStatus.Inactive, statusChangeDetails)
            Effect
              .persist(AgreementDeactivated(updatedAgreement))
              .thenRun((_: State) => replyTo ! StatusReply.Success(PersistentAgreement.toAPI(updatedAgreement)))
          }
          .getOrElse {
            replyTo ! StatusReply.Error[Agreement](s"Agreement ${agreementId} not found.")
            Effect.none[AgreementDeactivated, State]
          }

      case ListAgreements(from, to, producerId, consumerId, eserviceId, descriptorId, status, replyTo) =>
        val agreements: Seq[Agreement] = state.agreements
          .slice(from, to)
          .filter(agreement => producerId.forall(filter => filter == agreement._2.producerId.toString))
          .filter(agreement => consumerId.forall(filter => filter == agreement._2.consumerId.toString))
          .filter(agreement => eserviceId.forall(filter => filter == agreement._2.eserviceId.toString))
          .filter(agreement => descriptorId.forall(filter => filter == agreement._2.descriptorId.toString))
          .filter(agreement => status.forall(filter => filter == agreement._2.status.stringify))
          .values
          .toSeq
          .map(PersistentAgreement.toAPI)

        replyTo ! agreements
        Effect.none[Event, State]

      case Idle =>
        shard ! ClusterSharding.Passivate(context.self)
        context.log.error(s"Passivate shard: ${shard.path.name}")
        Effect.none[Event, State]
    }
  }

  val eventHandler: (State, Event) => State = (state, event) =>
    event match {
      case AgreementAdded(agreement)           => state.add(agreement)
      case AgreementActivated(agreement)       => state.updateAgreement(agreement)
      case AgreementSuspended(agreement)       => state.updateAgreement(agreement)
      case AgreementDeactivated(agreement)     => state.updateAgreement(agreement)
      case VerifiedAttributeUpdated(agreement) => state.updateAgreement(agreement)
    }

  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("uservice-agreement-management-persistence-agreement")

  def apply(shard: ActorRef[ClusterSharding.ShardCommand], persistenceId: PersistenceId): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.error(s"Starting Pet Shard ${persistenceId.id}")
      val numberOfEvents =
        context.system.settings.config
          .getInt("uservice-agreement-management.number-of-events-before-snapshot")
      EventSourcedBehavior[Command, Event, State](
        persistenceId = persistenceId,
        emptyState = State.empty,
        commandHandler = commandHandler(shard, context),
        eventHandler = eventHandler
      ).withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = numberOfEvents, keepNSnapshots = 1))
        .withTagger(_ => Set(persistenceId.id))
        .onPersistFailure(SupervisorStrategy.restartWithBackoff(200 millis, 5 seconds, 0.1))
    }
  }

  private def updateAgreementStatus(
    persistentAgreement: PersistentAgreement,
    status: PersistentAgreementStatus,
    statusChangeDetails: StatusChangeDetails
  ): PersistentAgreement = {

    def isSuspended = status == PersistentAgreementStatus.Suspended

    (statusChangeDetails.changedBy) match {
      case (Some(isConsumer)) if isConsumer == StatusChangeDetailsEnum.Consumer.stringify =>
        persistentAgreement.copy(status = status, suspendedByConsumer = Some(isSuspended))

      case (Some(isProducer)) if isProducer == StatusChangeDetailsEnum.Producer.stringify =>
        persistentAgreement.copy(status = status, suspendedByProducer = Some(isSuspended))

      case Some(_) => persistentAgreement.copy(status = status)
      case None    => persistentAgreement.copy(status = status)
    }

  }
}
