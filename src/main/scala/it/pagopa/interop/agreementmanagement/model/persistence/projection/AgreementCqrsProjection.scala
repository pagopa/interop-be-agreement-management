package it.pagopa.interop.agreementmanagement.model.persistence.projection

import akka.actor.typed.ActorSystem
import it.pagopa.interop.agreementmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.agreementmanagement.model.persistence._
import it.pagopa.interop.commons.cqrs.model._
import it.pagopa.interop.commons.cqrs.service.CqrsProjection
import it.pagopa.interop.commons.cqrs.service.DocumentConversions._
import org.mongodb.scala.model._
import org.mongodb.scala.{MongoCollection, _}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import spray.json._

import scala.concurrent.ExecutionContext

object AgreementCqrsProjection {
  def projection(offsetDbConfig: DatabaseConfig[JdbcProfile], mongoDbConfig: MongoDbConfig, projectionId: String)(
    implicit
    system: ActorSystem[_],
    ec: ExecutionContext
  ): CqrsProjection[Event] =
    CqrsProjection[Event](offsetDbConfig, mongoDbConfig, projectionId = projectionId, eventHandler)

  private def eventHandler(collection: MongoCollection[Document], event: Event): PartialMongoAction = event match {
    case AgreementAdded(a)                            =>
      ActionWithDocument(collection.insertOne, Document(s"{ data: ${a.toJson.compactPrint} }"))
    case AgreementUpdated(a)                          =>
      ActionWithBson(collection.updateOne(Filters.eq("data.id", a.id.toString), _), Updates.set("data", a.toDocument))
    case AgreementDeleted(aId)                        => Action(collection.deleteOne(Filters.eq("data.id", aId)))
    case AgreementContractAdded(aId, c)               =>
      ActionWithBson(collection.updateOne(Filters.eq("data.id", aId), _), Updates.set("data.contract", c.toDocument))
    case AgreementConsumerDocumentAdded(aId, doc)     =>
      ActionWithBson(
        collection.updateOne(Filters.eq("data.id", aId), _),
        Updates.push("data.consumerDocuments", doc.toDocument)
      )
    case AgreementConsumerDocumentRemoved(aId, docId) =>
      ActionWithBson(
        collection.updateOne(Filters.eq("data.id", aId), _),
        Updates.pull("data.consumerDocuments", Filters.eq("id", docId))
      )
    case VerifiedAttributeUpdated(_)                  => NoOpAction
    case AgreementActivated(a)                        =>
      ActionWithBson(collection.updateOne(Filters.eq("data.id", a.id.toString), _), Updates.set("data", a.toDocument))
    case AgreementSuspended(a)                        =>
      ActionWithBson(collection.updateOne(Filters.eq("data.id", a.id.toString), _), Updates.set("data", a.toDocument))
    case AgreementDeactivated(a)                      =>
      ActionWithBson(collection.updateOne(Filters.eq("data.id", a.id.toString), _), Updates.set("data", a.toDocument))
  }

}
