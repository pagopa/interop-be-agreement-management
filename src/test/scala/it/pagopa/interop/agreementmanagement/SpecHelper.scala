package it.pagopa.interop.agreementmanagement

import akka.actor
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpMethods, HttpResponse, MessageEntity}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import cats.syntax.all._
import it.pagopa.interop.agreementmanagement.model.AgreementState.{ACTIVE, PENDING, SUSPENDED}
import it.pagopa.interop.agreementmanagement.model._

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

trait SpecHelper {

  final val timestamp = OffsetDateTime.of(2022, 12, 31, 11, 22, 33, 44, ZoneOffset.UTC)

  val attributeId: UUID = UUID.randomUUID()

  object AgreementOne {
    val agreementId: UUID  = UUID.randomUUID()
    val eserviceId: UUID   = UUID.randomUUID()
    val descriptorId: UUID = UUID.randomUUID()
    val producerId: UUID   = UUID.randomUUID()
    val consumerId: UUID   = UUID.randomUUID()
  }

  object AgreementTwo {
    val agreementId: UUID  = UUID.randomUUID()
    val eserviceId: UUID   = UUID.randomUUID()
    val descriptorId: UUID = UUID.randomUUID()
    val consumerId: UUID   = UUID.randomUUID()
    val producerId: UUID   = UUID.randomUUID()
  }

  object AgreementThree {
    val agreementId: UUID  = UUID.randomUUID()
    val eserviceId: UUID   = UUID.randomUUID()
    val descriptorId: UUID = UUID.randomUUID()
    val consumerId: UUID   = UUID.randomUUID()
    val producerId: UUID   = UUID.randomUUID()
  }

  object AgreementFour {
    val agreementId: UUID  = UUID.randomUUID()
    val eserviceId: UUID   = UUID.randomUUID()
    val descriptorId: UUID = UUID.randomUUID()
    val consumerId: UUID   = UUID.randomUUID()
    val producerId: UUID   = UUID.randomUUID()
  }

  object Attributes {
    val id1: UUID = UUID.randomUUID()
    val id2: UUID = UUID.randomUUID()
    val id3: UUID = UUID.randomUUID()
  }

  def createAgreement(seed: AgreementSeed, agreementId: UUID)(implicit
    ec: ExecutionContext,
    actorSystem: actor.ActorSystem
  ): Future[Agreement] = for {
    data <- Marshal(seed).to[MessageEntity].map(_.dataBytes)
    _ = (() => mockUUIDSupplier.get()).expects().returning(agreementId).once()
    _ = (() => mockDateTimeSupplier.get()).expects().returning(timestamp).once()
    _ = seed.verifiedAttributes.foreach(_ => (() => mockDateTimeSupplier.get()).expects().returning(timestamp).once())
    agreement <- Unmarshal(makeRequest(data, "agreements", HttpMethods.POST)).to[Agreement]
  } yield agreement

  def getAgreement(id: String)(implicit ec: ExecutionContext, actorSystem: actor.ActorSystem): Future[Agreement] =
    Unmarshal(makeRequest(emptyData, s"agreements/$id", HttpMethods.GET)).to[Agreement]

  def updateAgreement(agreementId: UUID, updateAgreementSeed: UpdateAgreementSeed)(implicit
    ec: ExecutionContext,
    actorSystem: actor.ActorSystem
  ): Future[Agreement] = for {
    data <- Marshal(updateAgreementSeed)
      .to[MessageEntity]
      .map(_.dataBytes)
    _ = (() => mockDateTimeSupplier.get()).expects().returning(timestamp).once()
    updated <- Unmarshal(makeRequest(data, s"agreements/${agreementId.toString}/update", HttpMethods.POST))
      .to[Agreement]
  } yield updated

  def submitAgreement(agreement: Agreement, stamp: Stamp)(implicit
    ec: ExecutionContext,
    actorSystem: actor.ActorSystem
  ): Future[Agreement] =
    updateAgreement(
      agreement.id,
      UpdateAgreementSeed(
        state = PENDING,
        certifiedAttributes = agreement.certifiedAttributes,
        declaredAttributes = agreement.declaredAttributes,
        verifiedAttributes = agreement.verifiedAttributes,
        suspendedByConsumer = agreement.suspendedByConsumer,
        suspendedByProducer = agreement.suspendedByProducer,
        suspendedByPlatform = agreement.suspendedByPlatform,
        stamps = agreement.stamps.copy(submission = stamp.some)
      )
    )

  def activateAgreement(
    agreement: Agreement,
    stamp: Stamp,
    suspendedByConsumer: Option[Boolean] = None,
    suspendedByProducer: Option[Boolean] = None,
    suspendedByPlatform: Option[Boolean] = None
  )(implicit ec: ExecutionContext, actorSystem: actor.ActorSystem): Future[Agreement] = updateAgreement(
    agreement.id,
    UpdateAgreementSeed(
      state = ACTIVE,
      certifiedAttributes = agreement.certifiedAttributes,
      declaredAttributes = agreement.declaredAttributes,
      verifiedAttributes = agreement.verifiedAttributes,
      suspendedByConsumer = suspendedByConsumer orElse agreement.suspendedByConsumer,
      suspendedByProducer = suspendedByProducer orElse agreement.suspendedByProducer,
      suspendedByPlatform = suspendedByPlatform orElse agreement.suspendedByPlatform,
      stamps = agreement.stamps.copy(activation = stamp.some)
    )
  )

  def suspendAgreement(
    agreement: Agreement,
    stamp: Stamp,
    suspendedByConsumer: Option[Boolean] = None,
    suspendedByProducer: Option[Boolean] = None,
    suspendedByPlatform: Option[Boolean] = None
  )(implicit ec: ExecutionContext, actorSystem: actor.ActorSystem): Future[Agreement] = updateAgreement(
    agreement.id,
    UpdateAgreementSeed(
      state = SUSPENDED,
      certifiedAttributes = agreement.certifiedAttributes,
      declaredAttributes = agreement.declaredAttributes,
      verifiedAttributes = agreement.verifiedAttributes,
      suspendedByConsumer = suspendedByConsumer orElse agreement.suspendedByConsumer,
      suspendedByProducer = suspendedByProducer orElse agreement.suspendedByProducer,
      suspendedByPlatform = suspendedByPlatform orElse agreement.suspendedByPlatform,
      stamps = agreement.stamps.copy(suspensionByProducer = stamp.some)
    )
  )

  def upgradeAgreement(agreementId: String, newAgreementId: UUID, seed: UpgradeAgreementSeed)(implicit
    ec: ExecutionContext,
    actorSystem: actor.ActorSystem
  ): Future[Agreement] = for {
    data <- Marshal(seed).to[MessageEntity].map(_.dataBytes)
    _ = (() => mockUUIDSupplier.get()).expects().returning(newAgreementId).once()
    _ = (() => mockDateTimeSupplier.get()).expects().returning(timestamp).once()
    _ = (() => mockDateTimeSupplier.get()).expects().returning(timestamp).once()
    agreement <- Unmarshal(makeRequest(data, s"agreements/$agreementId/upgrade", HttpMethods.POST)).to[Agreement]
  } yield agreement

  def addContract[T](agreementId: UUID, seed: DocumentSeed)(implicit
    ec: ExecutionContext,
    actorSystem: actor.ActorSystem,
    unmarshal: Unmarshaller[HttpResponse, T]
  ): Future[T] = for {
    data <- Marshal(seed).to[MessageEntity].map(_.dataBytes)
    _ = (() => mockDateTimeSupplier.get()).expects().returning(timestamp).once()
    document <- Unmarshal(makeRequest(data, s"agreements/$agreementId/contract", HttpMethods.POST))
      .to[T]
  } yield document

  def addConsumerDocument[T](agreementId: UUID, seed: DocumentSeed)(implicit
    ec: ExecutionContext,
    actorSystem: actor.ActorSystem,
    unmarshal: Unmarshaller[HttpResponse, T]
  ): Future[T] = for {
    data <- Marshal(seed).to[MessageEntity].map(_.dataBytes)
    _ = (() => mockDateTimeSupplier.get()).expects().returning(timestamp).once()
    document <- Unmarshal(makeRequest(data, s"agreements/$agreementId/consumer-documents", HttpMethods.POST))
      .to[T]
  } yield document

  def removeConsumerDocument(agreementId: UUID, documentId: UUID)(implicit
    ec: ExecutionContext,
    actorSystem: actor.ActorSystem
  ): Future[String] = for {
    response <- Unmarshal(
      makeRequest(emptyData, s"agreements/$agreementId/consumer-documents/$documentId", HttpMethods.DELETE)
    ).to[String]
  } yield response

  def prepareDataForListingTests(implicit ec: ExecutionContext, actorSystem: actor.ActorSystem): Unit = {
    val agreementSeed1 = AgreementSeed(
      eserviceId = AgreementOne.eserviceId,
      descriptorId = AgreementOne.descriptorId,
      producerId = AgreementOne.producerId,
      consumerId = AgreementOne.consumerId,
      verifiedAttributes = Seq(AttributeSeed(id = Attributes.id1)),
      certifiedAttributes = Seq(AttributeSeed(id = Attributes.id2)),
      declaredAttributes = Seq(AttributeSeed(id = Attributes.id3))
    )

    val agreementSeed2 = agreementSeed1.copy(
      eserviceId = AgreementTwo.eserviceId,
      descriptorId = AgreementTwo.descriptorId,
      consumerId = AgreementTwo.consumerId,
      producerId = AgreementTwo.producerId,
      verifiedAttributes = Seq(AttributeSeed(id = Attributes.id1)),
      certifiedAttributes = Seq(AttributeSeed(id = Attributes.id2)),
      declaredAttributes = Seq(AttributeSeed(id = Attributes.id3))
    )

    val agreementSeed3 = agreementSeed1.copy(
      eserviceId = AgreementThree.eserviceId,
      descriptorId = AgreementThree.descriptorId,
      consumerId = AgreementThree.consumerId,
      producerId = AgreementThree.producerId,
      verifiedAttributes = Seq(AttributeSeed(id = Attributes.id1)),
      certifiedAttributes = Seq(AttributeSeed(id = Attributes.id2)),
      declaredAttributes = Seq(AttributeSeed(id = Attributes.id3))
    )

    val agreementSeed4 = agreementSeed1.copy(
      eserviceId = AgreementFour.eserviceId,
      descriptorId = AgreementFour.descriptorId,
      consumerId = AgreementFour.consumerId,
      producerId = AgreementFour.producerId,
      verifiedAttributes = Seq(AttributeSeed(id = Attributes.id1)),
      certifiedAttributes = Seq(AttributeSeed(id = Attributes.id2)),
      declaredAttributes = Seq(AttributeSeed(id = Attributes.id3))
    )

    val complete = for {
      _      <- createAgreement(agreementSeed1, AgreementOne.agreementId)
      draft1 <- createAgreement(agreementSeed2, AgreementTwo.agreementId)
      _      <- updateAgreement(
        draft1.id,
        UpdateAgreementSeed(
          state = ACTIVE,
          certifiedAttributes = Seq(CertifiedAttribute(attributeId)),
          declaredAttributes = draft1.declaredAttributes,
          verifiedAttributes = draft1.verifiedAttributes,
          suspendedByConsumer = None,
          suspendedByProducer = None,
          suspendedByPlatform = None,
          stamps = Stamps(activation = Stamp(who = UUID.randomUUID(), when = OffsetDateTime.now()).some)
        )
      )
      draft2 <- createAgreement(agreementSeed3, AgreementThree.agreementId)
      _      <- updateAgreement(
        draft2.id,
        UpdateAgreementSeed(
          state = SUSPENDED,
          certifiedAttributes = draft2.certifiedAttributes,
          declaredAttributes = Seq(DeclaredAttribute(attributeId)),
          verifiedAttributes = draft2.verifiedAttributes,
          suspendedByConsumer = None,
          suspendedByProducer = None,
          suspendedByPlatform = None,
          stamps = Stamps(suspensionByProducer = Stamp(who = UUID.randomUUID(), when = OffsetDateTime.now()).some)
        )
      )
      draft4 <- createAgreement(agreementSeed4, AgreementFour.agreementId)
      _      <- updateAgreement(
        draft4.id,
        UpdateAgreementSeed(
          state = PENDING,
          certifiedAttributes = draft4.certifiedAttributes,
          declaredAttributes = draft4.declaredAttributes,
          verifiedAttributes = Seq(VerifiedAttribute(attributeId)),
          suspendedByConsumer = None,
          suspendedByProducer = None,
          suspendedByPlatform = None,
          stamps = Stamps(submission = Stamp(who = UUID.randomUUID(), when = OffsetDateTime.now()).some)
        )
      )
    } yield ()

    Await.result(complete, Duration.Inf)
  }
}
