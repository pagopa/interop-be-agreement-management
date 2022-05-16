package it.pagopa.interop.agreementmanagement.model.persistence.projection

import akka.Done
import akka.actor.typed.ActorSystem
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.ProjectionId
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.{ExactlyOnceProjection, SourceProvider}
import akka.projection.slick.{SlickHandler, SlickProjection}
import cats.syntax.all._
import com.typesafe.scalalogging.Logger
import it.pagopa.interop.agreementmanagement.common.system.MongoDbConfig
import it.pagopa.interop.agreementmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.agreementmanagement.model.persistence._
import it.pagopa.interop.agreementmanagement.model.persistence.projection.models.{CqrsMetadata, SourceEvent}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala._
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.connection.NettyStreamFactoryFactory
import org.mongodb.scala.model._
import slick.basic.DatabaseConfig
import slick.dbio._
import slick.jdbc.JdbcProfile
import spray.json._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

final case class AgreementCqrsProjection(offsetDbConfig: DatabaseConfig[JdbcProfile], mongoDbConfig: MongoDbConfig)(
  implicit
  system: ActorSystem[_],
  ec: ExecutionContext
) {

  private val client: MongoClient = MongoClient(
    MongoClientSettings
      .builder()
      .applyConnectionString(new ConnectionString(mongoDbConfig.connectionString))
      .codecRegistry(DEFAULT_CODEC_REGISTRY)
      .streamFactoryFactory(NettyStreamFactoryFactory())
      .build()
  )

  def sourceProvider(tag: String): SourceProvider[Offset, EventEnvelope[Event]] =
    EventSourcedProvider
      .eventsByTag[Event](system, readJournalPluginId = JdbcReadJournal.Identifier, tag = tag)

  def projection(tag: String): ExactlyOnceProjection[Offset, EventEnvelope[Event]] = SlickProjection.exactlyOnce(
    projectionId = ProjectionId("agreement-cqrs-projections", tag),
    sourceProvider = sourceProvider(tag),
    handler = () => CqrsProjectionHandler(client, mongoDbConfig.dbName, mongoDbConfig.collectionName),
    databaseConfig = offsetDbConfig
  )
}

final case class CqrsProjectionHandler(client: MongoClient, dbName: String, collectionName: String)(implicit
  ec: ExecutionContext
) extends SlickHandler[EventEnvelope[Event]] {

  private val logger: Logger = Logger(this.getClass)

  // Note: the implementation is not idempotent
  override def process(envelope: EventEnvelope[Event]): DBIO[Done] = DBIOAction.from {
    logger.debug(s"CQRS Projection: writing event with envelop $envelope")
    val collection: MongoCollection[Document] = client.getDatabase(dbName).getCollection(collectionName)

    val metadata: CqrsMetadata = CqrsMetadata(sourceEvent =
      SourceEvent(
        persistenceId = envelope.persistenceId,
        sequenceNr = envelope.sequenceNr,
        timestamp = envelope.timestamp
      )
    )

    def withMetadata(op: Bson): Bson = Updates.combine(Updates.set("metadata", metadata.toDocument), op)

    val result = envelope.event match {
      case AgreementAdded(a)           =>
        collection.insertOne(Document(s"{ data: ${a.toJson.compactPrint}, metadata: ${metadata.toJson.compactPrint} }"))
      case AgreementActivated(a)       =>
        collection.updateOne(Filters.eq("data.id", a.id.toString), withMetadata(Updates.set("data", a.toDocument)))
      case AgreementSuspended(a)       =>
        collection.updateOne(Filters.eq("data.id", a.id.toString), withMetadata(Updates.set("data", a.toDocument)))
      case AgreementDeactivated(a)     =>
        collection.updateOne(Filters.eq("data.id", a.id.toString), withMetadata(Updates.set("data", a.toDocument)))
      case VerifiedAttributeUpdated(a) =>
        collection.updateOne(Filters.eq("data.id", a.id.toString), withMetadata(Updates.set("data", a.toDocument)))
    }

    val futureResult = result.toFuture()

    futureResult.onComplete {
      case Failure(e) => logger.error(s"Error on CQRS sink for ${metadata.show}", e)
      case Success(_) => logger.debug(s"CQRS sink completed for ${metadata.show}")
    }
    futureResult.as(Done)
  }

  implicit class SerializableToDocument[T: JsonWriter](v: T) extends AnyRef {
    def toDocument = Document(v.toJson.compactPrint)
  }

}
