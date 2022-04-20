package it.pagopa.interop.agreementmanagement.model.persistence

import akka.Done
import akka.actor.typed.ActorSystem
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.ProjectionId
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.{ExactlyOnceProjection, SourceProvider}
import akka.projection.slick.{SlickHandler, SlickProjection}
import slick.basic.DatabaseConfig
import slick.dbio._
import slick.jdbc.JdbcProfile
import cats.syntax.all._
import it.pagopa.interop.commons.queue.QueueWriter
import scala.concurrent.ExecutionContext
import it.pagopa.interop.commons.queue.message.Message
import java.util.UUID
import it.pagopa.interop.commons.queue.message.ProjectableEvent

class AgreementPersistentProjection(
  dbConfig: DatabaseConfig[JdbcProfile],
  queueWriter: QueueWriter,
  queueWriterEc: ExecutionContext
)(implicit system: ActorSystem[_]) {

  def sourceProvider(tag: String): SourceProvider[Offset, EventEnvelope[Event]] =
    EventSourcedProvider
      .eventsByTag[Event](system, readJournalPluginId = JdbcReadJournal.Identifier, tag = tag)

  def projection(tag: String): ExactlyOnceProjection[Offset, EventEnvelope[Event]] = SlickProjection.exactlyOnce(
    projectionId = ProjectionId("agreement-projections", tag),
    sourceProvider = sourceProvider(tag),
    handler = () => new ProjectionHandler(queueWriter)(queueWriterEc),
    databaseConfig = dbConfig
  )

}

class ProjectionHandler(queueWriter: QueueWriter)(implicit ec: ExecutionContext)
    extends SlickHandler[EventEnvelope[Event]] {

  def innerSend(message: Message): DBIO[Done] = DBIOAction.from(queueWriter.send(message).as(Done))

  val message: EventEnvelope[Event] => ((String, ProjectableEvent) => Message) = envelope => { case (kind, event) =>
    Message(UUID.randomUUID(), envelope.persistenceId, envelope.sequenceNr, envelope.timestamp, kind, event)
  }

  override def process(envelope: EventEnvelope[Event]): DBIO[Done] = {
    def send(kind: String, x: ProjectableEvent) = innerSend(message(envelope)(kind, x))
    val kind: String                            = AgreementEventsSerde.getKind(envelope.event)
    send(kind, envelope.event)
  }

}
