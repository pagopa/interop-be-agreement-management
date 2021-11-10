package it.pagopa.pdnd.interop.uservice.agreementmanagement.api.impl

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onSuccess
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import cats.implicits.toTraverseOps
import it.pagopa.pdnd.interop.uservice.agreementmanagement.api.AgreementApiService
import it.pagopa.pdnd.interop.uservice.agreementmanagement.common.system._
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model._
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.agreement.{
  PersistentAgreement,
  PersistentAgreementState
}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence._
import it.pagopa.pdnd.interop.uservice.agreementmanagement.service.UUIDSupplier

import scala.concurrent._

class AgreementApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]],
  UUIDSupplier: UUIDSupplier
)(implicit ec: ExecutionContext)
    extends AgreementApiService {

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
    val agreement: PersistentAgreement         = PersistentAgreement.fromAPI(agreementSeed, UUIDSupplier)
    val result: Future[StatusReply[Agreement]] = createAgreement(agreement)
    onSuccess(result) {
      case statusReply if statusReply.isSuccess => addAgreement200(statusReply.getValue)
      case statusReply if statusReply.isError =>
        addAgreement405(Problem(Option(statusReply.getError.getMessage), status = 405, "some error"))
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

  override def activateAgreement(agreementId: String, stateChangeDetails: StateChangeDetails)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement],
    contexts: Seq[(String, String)]
  ): Route = {
    val result: Future[StatusReply[Agreement]] = activateAgreementById(agreementId, stateChangeDetails)
    onSuccess(result) {
      case statusReply if statusReply.isSuccess => activateAgreement200(statusReply.getValue)
      case statusReply if statusReply.isError =>
        activateAgreement404(Problem(Option(statusReply.getError.getMessage), status = 404, "some error"))
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
    val result: Future[StatusReply[Agreement]] = suspendAgreementById(agreementId, stateChangeDetails)
    onSuccess(result) {
      case statusReply if statusReply.isSuccess => suspendAgreement200(statusReply.getValue)
      case statusReply if statusReply.isError =>
        suspendAgreement404(Problem(Option(statusReply.getError.getMessage), status = 404, "some error"))
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
    contexts.foreach(println)
    val sliceSize = 100

    val commanders: Seq[EntityRef[Command]] = (0 until settings.numberOfShards).map(shard =>
      sharding.entityRefFor(AgreementPersistentBehavior.TypeKey, shard.toString)
    )

    val result = for {
      stateEnum <- state.traverse(AgreementState.fromValue)
      generator = createListAgreementsGenerator(
        producerId = producerId,
        consumerId = consumerId,
        eserviceId = eserviceId,
        descriptorId = descriptorId,
        state = stateEnum
      )(_, _)
      agreements = commanders.flatMap(ref => slices(ref, sliceSize)(generator))
    } yield agreements

    result match {
      case Right(agreements) => getAgreements200(agreements)
      case Left(error)       => getAgreements400(Problem(Option(error.getMessage), 400, "Error on agreements retrieve"))
    }

  }

  private def createListAgreementsGenerator(
    producerId: Option[String],
    consumerId: Option[String],
    eserviceId: Option[String],
    descriptorId: Option[String],
    state: Option[AgreementState]
  )(from: Int, to: Int): ActorRef[Seq[Agreement]] => ListAgreements =
    (ref: ActorRef[Seq[Agreement]]) =>
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
    val commander: EntityRef[Command] =
      sharding.entityRefFor(AgreementPersistentBehavior.TypeKey, getShard(agreementId))
    val result: Future[StatusReply[Agreement]] =
      commander.ask(ref => UpdateVerifiedAttribute(agreementId, verifiedAttributeSeed, ref))
    onSuccess(result) {
      case statusReply if statusReply.isSuccess => updateAgreementVerifiedAttribute200(statusReply.getValue)
      case statusReply if statusReply.isError =>
        updateAgreementVerifiedAttribute404(
          Problem(Option(statusReply.getError.getMessage), status = 404, "Verified Attribute not found")
        )
    }
  }

  //TODO introduce proper uuid handling (e.g.: Twitter snowflake)
  override def upgradeAgreementById(agreementId: String, agreementSeed: AgreementSeed)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement],
    contexts: Seq[(String, String)]
  ): Route = {
    val result = for {
      _ <- deactivateAgreementById(agreementId, StateChangeDetails(changedBy = None))
      persistentAgreement = PersistentAgreement.fromAPIWithActiveState(agreementSeed, UUIDSupplier)
      activeAgreement <- createAgreement(persistentAgreement)
    } yield activeAgreement

    onSuccess(result) {
      case statusReply if statusReply.isSuccess => upgradeAgreementById200(statusReply.getValue)
      case statusReply if statusReply.isError =>
        upgradeAgreementById400(Problem(Option(statusReply.getError.getMessage), status = 404, "some error"))
    }
  }

  private def deactivateAgreementById(agreementId: String, stateChangeDetails: StateChangeDetails) = {
    val commander: EntityRef[Command] =
      sharding.entityRefFor(AgreementPersistentBehavior.TypeKey, getShard(agreementId))

    commander.ask(ref => DeactivateAgreement(agreementId, stateChangeDetails, ref))
  }
}
