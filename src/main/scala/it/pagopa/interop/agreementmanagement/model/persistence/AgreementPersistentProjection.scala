package it.pagopa.interop.agreementmanagement.model.persistence

import akka.Done
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.ProjectionId
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.{ExactlyOnceProjection, SourceProvider}
import akka.projection.slick.{SlickHandler, SlickProjection}
import org.slf4j.LoggerFactory
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile

final case class AgreementPersistentProjection(
  system: ActorSystem[_],
  entity: Entity[Command, ShardingEnvelope[Command]],
  dbConfig: DatabaseConfig[JdbcProfile]
) {

  def sourceProvider(tag: String): SourceProvider[Offset, EventEnvelope[Event]] =
    EventSourcedProvider
      .eventsByTag[Event](system, readJournalPluginId = JdbcReadJournal.Identifier, tag = tag)

  def projection(tag: String): ExactlyOnceProjection[Offset, EventEnvelope[Event]] = {
    implicit val as: ActorSystem[_] = system
    SlickProjection.exactlyOnce(
      projectionId = ProjectionId("agreement-projections", tag),
      sourceProvider = sourceProvider(tag),
      handler = () => new ProjectionHandler(tag),
      databaseConfig = dbConfig
    )
  }
}

class ProjectionHandler(tag: String) extends SlickHandler[EventEnvelope[Event]] {

  val logger = LoggerFactory.getLogger(this.getClass)

  override def process(envelope: EventEnvelope[Event]) = {
    envelope.event match {
      case _ =>
        logger.debug("This is the envelope event payload > {}", envelope.event)
        logger.debug("On tagged projection > {}", tag)
        DBIOAction.successful(Done)
    }
  }

}
