package it.pagopa.interop.agreementmanagement.api.impl

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementmanagement.api.AgreementApiService
import it.pagopa.interop.agreementmanagement.api.impl.ResponseHandlers._
import it.pagopa.interop.agreementmanagement.model.AgreementState.ARCHIVED
import it.pagopa.interop.agreementmanagement.model._
import it.pagopa.interop.agreementmanagement.model.agreement._
import it.pagopa.interop.agreementmanagement.model.persistence.Adapters._
import it.pagopa.interop.agreementmanagement.model.persistence._
import it.pagopa.interop.commons.jwt._
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getShard
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}

import scala.concurrent._
import scala.concurrent.duration._

final case class AgreementApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]],
  UUIDSupplier: UUIDSupplier,
  dateTimeSupplier: OffsetDateTimeSupplier
)(implicit ec: ExecutionContext)
    extends AgreementApiService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  implicit val timeout: Timeout = 300.seconds

  private val settings: ClusterShardingSettings = entity.settings.getOrElse(ClusterShardingSettings(system))

  override def addAgreement(agreementSeed: AgreementSeed)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {
    val operationLabel =
      s"Adding an Agreement for consumer ${agreementSeed.consumerId} descriptor ${agreementSeed.descriptorId} EService ${agreementSeed.eserviceId} Producer ${agreementSeed.producerId}"
    logger.info(operationLabel)

    val agreement: PersistentAgreement      = PersistentAgreement.fromAPI(agreementSeed, UUIDSupplier, dateTimeSupplier)
    val result: Future[PersistentAgreement] = commander(agreement.id.toString).askWithStatus(AddAgreement(agreement, _))

    onComplete(result) {
      addAgreementResponse[PersistentAgreement](operationLabel)(agreement =>
        addAgreement200(PersistentAgreement.toAPI(agreement))
      )
    }
  }

  override def updateAgreementById(agreementId: String, updateAgreementSeed: UpdateAgreementSeed)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE, M2M_ROLE, INTERNAL_ROLE) {
    val operationLabel: String = s"Updating agreement $agreementId"
    logger.info(operationLabel)

    val result: Future[PersistentAgreement] =
      for {
        agreement <- commander(agreementId).askWithStatus(ref => GetAgreement(agreementId, ref))
        updated = PersistentAgreement.update(agreement, updateAgreementSeed, dateTimeSupplier)
        result <- commander(agreementId).askWithStatus(ref => UpdateAgreement(updated, ref))
      } yield result

    onComplete(result) {
      updateAgreementByIdResponse[PersistentAgreement](operationLabel)(agreement =>
        updateAgreementById200(PersistentAgreement.toAPI(agreement))
      )
    }
  }

  override def deleteAgreement(
    agreementId: String
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem], contexts: Seq[(String, String)]): Route =
    authorize(ADMIN_ROLE) {
      val operationLabel: String = s"Deleting agreement $agreementId"
      logger.info(operationLabel)

      val result: Future[Unit] = commander(agreementId).askWithStatus(DeleteAgreement(agreementId, _))

      onComplete(result) {
        deleteAgreementResponse[Unit](operationLabel)(_ => deleteAgreement204)
      }
    }

  override def getAgreement(agreementId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE) {
    val operationLabel: String = s"Getting agreement $agreementId"
    logger.info(operationLabel)

    val result: Future[PersistentAgreement] = commander(agreementId).askWithStatus(GetAgreement(agreementId, _))

    onComplete(result) {
      getAgreementResponse[PersistentAgreement](operationLabel)(agreement =>
        getAgreement200(PersistentAgreement.toAPI(agreement))
      )
    }
  }

  override def getAgreements(
    producerId: Option[String],
    consumerId: Option[String],
    eserviceId: Option[String],
    descriptorId: Option[String],
    states: String,
    attributeId: Option[String]
  )(implicit
    toEntityMarshallerAgreementarray: ToEntityMarshaller[Seq[Agreement]],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE, INTERNAL_ROLE) {

    val resourceId: String =
      s"""
         |producer=${producerId.getOrElse("")}/
         |consumer=${consumerId.getOrElse("")}/
         |eservice=${eserviceId.getOrElse("")}/
         |descriptor=${descriptorId.getOrElse("")}/
         |attributeId=${attributeId.getOrElse("")}/
         |states=$states
         |""".stripMargin.replaceAll("\n", "")

    val operationLabel: String = s"Getting agreements with $resourceId"
    logger.info(operationLabel)

    val sliceSize = 100

    val commanders: Seq[EntityRef[Command]] =
      (0 until settings.numberOfShards).map(shard =>
        sharding.entityRefFor(AgreementPersistentBehavior.TypeKey, shard.toString)
      )

    val result: Either[Throwable, Seq[PersistentAgreement]] = for {
      statesEnums <- parseArrayParameters(states).traverse(AgreementState.fromValue)
      generator  = createListAgreementsGenerator(
        producerId = producerId,
        consumerId = consumerId,
        eserviceId = eserviceId,
        descriptorId = descriptorId,
        attributeId = attributeId,
        states = statesEnums
      )(_, _)
      agreements = commanders.flatMap(ref => slices(ref, sliceSize)(generator))
    } yield agreements

    getAgreementsResponse[Seq[PersistentAgreement]](operationLabel)(agreements =>
      getAgreements200(agreements.map(PersistentAgreement.toAPI))
    )(result)(contexts, logger)
  }

  private def createListAgreementsGenerator(
    producerId: Option[String],
    consumerId: Option[String],
    eserviceId: Option[String],
    descriptorId: Option[String],
    attributeId: Option[String],
    states: List[AgreementState]
  )(from: Int, to: Int): ActorRef[Seq[PersistentAgreement]] => ListAgreements =
    (ref: ActorRef[Seq[PersistentAgreement]]) =>
      ListAgreements(
        from,
        to,
        producerId,
        consumerId,
        eserviceId,
        descriptorId,
        attributeId,
        states.map(PersistentAgreementState.fromApi),
        ref
      )

  override def addAgreementContract(agreementId: String, documentSeed: DocumentSeed)(implicit
    toEntityMarshallerDocument: ToEntityMarshaller[Document],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {
    val operationLabel: String = s"Adding contract ${documentSeed.id} to Agreement $agreementId"
    logger.info(operationLabel)

    val result: Future[PersistentAgreementDocument] =
      commander(agreementId).askWithStatus(ref =>
        AddAgreementContract(agreementId, PersistentAgreementDocument.fromAPI(documentSeed)(dateTimeSupplier), ref)
      )

    onComplete(result) {
      addAgreementContactResponse[PersistentAgreementDocument](operationLabel)(document =>
        addAgreementContract200(PersistentAgreementDocument.toAPI(document))
      )
    }
  }

  override def addAgreementConsumerDocument(agreementId: String, documentSeed: DocumentSeed)(implicit
    toEntityMarshallerAgreement: ToEntityMarshaller[Document],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {
    val operationLabel: String = s"Adding consumer document ${documentSeed.id} to Agreement $agreementId"
    logger.info(operationLabel)

    val result: Future[PersistentAgreementDocument] =
      commander(agreementId).askWithStatus(ref =>
        AddAgreementConsumerDocument(
          agreementId,
          PersistentAgreementDocument.fromAPI(documentSeed)(dateTimeSupplier),
          ref
        )
      )

    onComplete(result) {
      addAgreementConsumerDocumentResponse[PersistentAgreementDocument](operationLabel)(document =>
        addAgreementConsumerDocument200(PersistentAgreementDocument.toAPI(document))
      )
    }
  }

  override def removeAgreementConsumerDocument(agreementId: String, documentId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {
    val operationLabel: String = s"Removing consumer document $documentId from agreement $agreementId"
    logger.info(operationLabel)

    val result: Future[Unit] =
      commander(agreementId).askWithStatus(ref => RemoveAgreementConsumerDocument(agreementId, documentId, ref))

    onComplete(result) {
      removeAgreementConsumerDocumentResponse[Unit](operationLabel)(_ => removeAgreementConsumerDocument204)
    }
  }

  override def getAgreementConsumerDocument(agreementId: String, documentId: String)(implicit
    toEntityMarshallerDocument: ToEntityMarshaller[Document],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {
    val operationLabel: String = s"Retrieving consumer document $documentId from agreement $agreementId"
    logger.info(operationLabel)

    val result: Future[PersistentAgreementDocument] =
      commander(agreementId).askWithStatus(ref => GetAgreementConsumerDocument(agreementId, documentId, ref))

    onComplete(result) {
      getAgreementConsumerDocumentResponse[PersistentAgreementDocument](operationLabel)(document =>
        getAgreementConsumerDocument200(PersistentAgreementDocument.toAPI(document))
      )
    }
  }

  override def upgradeAgreementById(agreementId: String, seed: UpgradeAgreementSeed)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {
    val operationLabel: String = s"Upgrading agreement $agreementId"
    logger.info(s"$operationLabel, with data $seed")

    val result: Future[PersistentAgreement] = for {
      oldAgreement <- commander(agreementId).askWithStatus(ref => GetAgreement(agreementId, ref))
      deactivated = PersistentAgreement.update(
        oldAgreement,
        UpdateAgreementSeed(
          state = ARCHIVED,
          certifiedAttributes = oldAgreement.certifiedAttributes.map(PersistentCertifiedAttribute.toAPI),
          declaredAttributes = oldAgreement.declaredAttributes.map(PersistentDeclaredAttribute.toAPI),
          verifiedAttributes = oldAgreement.verifiedAttributes.map(PersistentVerifiedAttribute.toAPI),
          suspendedByConsumer = oldAgreement.suspendedByConsumer,
          suspendedByProducer = oldAgreement.suspendedByProducer,
          suspendedByPlatform = oldAgreement.suspendedByPlatform,
          stamps = PersistentStamps.toAPI(oldAgreement.stamps).copy(archiving = seed.stamp.some)
        ),
        dateTimeSupplier
      )
      _ <- commander(agreementId).askWithStatus(ref => UpdateAgreement(deactivated, ref))
      persistentAgreement = PersistentAgreement.upgrade(oldAgreement, seed)(UUIDSupplier, dateTimeSupplier)
      activeAgreement <- commander(persistentAgreement.id.toString).askWithStatus(ref =>
        AddAgreement(persistentAgreement, ref)
      )
    } yield activeAgreement

    onComplete(result) {
      upgradeAgreementByIdResponse[PersistentAgreement](operationLabel)(agreement =>
        upgradeAgreementById200(PersistentAgreement.toAPI(agreement))
      )
    }
  }

  private def commander(id: String): EntityRef[Command] =
    sharding.entityRefFor(AgreementPersistentBehavior.TypeKey, getShard(id, settings.numberOfShards))
}
