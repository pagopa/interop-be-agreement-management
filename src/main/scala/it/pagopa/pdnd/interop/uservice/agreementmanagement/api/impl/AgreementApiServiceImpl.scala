package it.pagopa.pdnd.interop.uservice.agreementmanagement.api.impl

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onSuccess
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import it.pagopa.pdnd.interop.uservice.agreementmanagement.api.AgreementApiService
import it.pagopa.pdnd.interop.uservice.agreementmanagement.common.system._
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence._
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.{Agreement, AgreementSeed, Problem, VerifiedAttribute}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.service.UUIDSupplier

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.Any",
    "org.wartremover.warts.Nothing",
    "org.wartremover.warts.Recursion"
  )
)
class AgreementApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]],
  UUIDSupplier: UUIDSupplier
) extends AgreementApiService {

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
    val id = UUIDSupplier.get
    val agreement: Agreement = Agreement(
      id = id,
      eserviceId = agreementSeed.eserviceId,
      producerId = agreementSeed.producerId,
      consumerId = agreementSeed.consumerId,
      status = "active",
      verifiedAttributes = agreementSeed.verifiedAttributes.distinctBy(_.id)
    )
    val commander: EntityRef[Command] =
      sharding.entityRefFor(AgreementPersistentBehavior.TypeKey, getShard(id.toString))
    val result: Future[StatusReply[Agreement]] = commander.ask(ref => AddAgreement(agreement, ref))
    onSuccess(result) {
      case statusReply if statusReply.isSuccess => addAgreement200(agreement)
      case statusReply if statusReply.isError =>
        addAgreement405(Problem(Option(statusReply.getError.getMessage), status = 405, "some error"))
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
  ): Route = {
    val commander: EntityRef[Command] =
      sharding.entityRefFor(AgreementPersistentBehavior.TypeKey, getShard(agreementId))
    val result: Future[StatusReply[Option[Agreement]]] = commander.ask(ref => GetAgreement(agreementId, ref))
    onSuccess(result) {
      case statusReply if statusReply.isSuccess =>
        statusReply.getValue.fold(getAgreement404(Problem(None, status = 404, "some error")))(agreement =>
          getAgreement200(agreement)
        )
      case statusReply if statusReply.isError =>
        getAgreement400(Problem(Option(statusReply.getError.getMessage), status = 400, "some error"))
    }
  }

  /** Code: 200, Message: A list of Agreement, DataType: Seq[Agreement]
    */
  override def getAgreements(producerId: Option[String], consumerId: Option[String], status: Option[String])(implicit
    toEntityMarshallerAgreementarray: ToEntityMarshaller[Seq[Agreement]],
    contexts: Seq[(String, String)]
  ): Route = {
    contexts.foreach(println)
    val sliceSize = 100
    def getSlice(commander: EntityRef[Command], from: Int, to: Int): LazyList[Agreement] = {
      val slice: Seq[Agreement] = Await
        .result(commander.ask(ref => ListAgreements(from, to, producerId, consumerId, status, ref)), Duration.Inf)

      if (slice.isEmpty)
        LazyList.empty[Agreement]
      else
        getSlice(commander, to, to + sliceSize) #::: slice.to(LazyList)
    }
    val commanders: Seq[EntityRef[Command]] = (0 until settings.numberOfShards).map(shard =>
      sharding.entityRefFor(AgreementPersistentBehavior.TypeKey, getShard(shard.toString))
    )
    val agreements: Seq[Agreement] = commanders.flatMap(ref => getSlice(ref, 0, sliceSize))

    getAgreements200(agreements)
  }

  /** Code: 200, Message: Returns the agreement with the updated attribute state., DataType: Agreement
    * Code: 400, Message: Bad Request, DataType: Problem
    * Code: 404, Message: Resource Not Found, DataType: Problem
    */
  override def updateAgreementVerifiedAttribute(agreementId: String, verifiedAttribute: VerifiedAttribute)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement],
    contexts: Seq[(String, String)]
  ): Route = {
    val commander: EntityRef[Command] =
      sharding.entityRefFor(AgreementPersistentBehavior.TypeKey, getShard(agreementId))
    val result: Future[StatusReply[Agreement]] =
      commander.ask(ref => UpdateVerifiedAttribute(agreementId, verifiedAttribute, ref))
    onSuccess(result) {
      case statusReply if statusReply.isSuccess => updateAgreementVerifiedAttribute200(statusReply.getValue)
      case statusReply if statusReply.isError =>
        updateAgreementVerifiedAttribute404(
          Problem(Option(statusReply.getError.getMessage), status = 404, "Verified Attribute not found")
        )
    }
  }
}
