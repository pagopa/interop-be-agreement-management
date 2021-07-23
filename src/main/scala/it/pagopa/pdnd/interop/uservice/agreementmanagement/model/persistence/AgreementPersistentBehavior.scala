package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.Agreement

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
        val agreement: Option[Agreement] = state.agreements.get(newAgreement.id.toString)
        agreement
          .map { es =>
            replyTo ! StatusReply.Error[Agreement](s"Agreement ${es.id.toString} already exists")
            Effect.none[AgreementAdded, State]
          }
          .getOrElse {
            Effect
              .persist(AgreementAdded(newAgreement))
              .thenRun((_: State) => replyTo ! StatusReply.Success(newAgreement))
          }

      case UpdateVerifiedAttribute(agreementId, updateVerifiedAttribute, replyTo) =>
        val attributeId = updateVerifiedAttribute.id.toString
        val agreementOpt: Option[Agreement] =
          state.getAgreementContainingVerifiedAttribute(agreementId, attributeId)
        agreementOpt
          .map { agreement =>
            val updatedAgreement = state.updateAgreementContent(agreement, updateVerifiedAttribute)
            Effect
              .persist(VerifiedAttributeUpdated(updatedAgreement))
              .thenRun((_: State) => replyTo ! StatusReply.Success(updatedAgreement))
          }
          .getOrElse {
            replyTo ! StatusReply.Error[Agreement](s"Attribute ${attributeId} not found for agreement ${agreementId}!")
            Effect.none[VerifiedAttributeUpdated, State]
          }

      case GetAgreement(agreementId, replyTo) =>
        val agreement: Option[Agreement] = state.agreements.get(agreementId)
        replyTo ! StatusReply.Success[Option[Agreement]](agreement)
        Effect.none[Event, State]

      case ListAgreements(from, to, producerId, _, status, replyTo) =>
        val agreements: Seq[Agreement] = state.agreements
          .filter { case (_, v) =>
            (if (producerId.isDefined) producerId.contains(v.producerId.toString) else true) &&
              (if (status.isDefined) status.contains(v.status) else true)
          }
          .values
          .toSeq
          .slice(from, to)
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
}
