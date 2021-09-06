package it.pagopa.pdnd.interop.uservice.agreementmanagement

import akka.actor
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpMethods, MessageEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.{Agreement, AgreementSeed, VerifiedAttributeSeed}

import java.util.UUID
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

trait SpecHelper {

  object AgreementOne {
    val agreementId: UUID = UUID.fromString("17f8dce0-0a5b-476b-9fdd-a7a658eb9210")
    val eserviceId: UUID  = UUID.fromString("17f8dce0-0a5b-476b-9fdd-a7a658eb9211")
    val producerId: UUID  = UUID.fromString("17f8dce0-0a5b-476b-9fdd-a7a658eb9212")
    val consumerId: UUID  = UUID.fromString("17f8dce0-0a5b-476b-9fdd-a7a658eb9213")
  }

  object AgreementTwo {
    val agreementId: UUID = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9210")
    val eserviceId: UUID  = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9211")
    val consumerId: UUID  = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9212")
    val producerId: UUID  = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9213")
  }

  object AgreementThree {
    val agreementId: UUID = UUID.fromString("47f8dce0-0a5b-476b-9fdd-a7a658eb9210")
    val eserviceId: UUID  = UUID.fromString("47f8dce0-0a5b-476b-9fdd-a7a658eb9211")
    val consumerId: UUID  = UUID.fromString("47f8dce0-0a5b-476b-9fdd-a7a658eb9212")
    val producerId: UUID  = UUID.fromString("47f8dce0-0a5b-476b-9fdd-a7a658eb9213")
  }

  object Attributes {
    val id1: UUID = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9215")
    val id2: UUID = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9216")
    val id3: UUID = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9217")
  }

  def createAgreement(
    seed: AgreementSeed
  )(implicit ec: ExecutionContext, actorSystem: actor.ActorSystem): Future[Agreement] = for {
    data      <- Marshal(seed).to[MessageEntity].map(_.dataBytes)
    agreement <- Unmarshal(makeRequest(data, "agreements", HttpMethods.POST)).to[Agreement]
  } yield agreement

  def activateAgreement(
    agreement: Agreement
  )(implicit ec: ExecutionContext, actorSystem: actor.ActorSystem): Future[Agreement] = for {
    activated <- Unmarshal(makeRequest(emptyData, s"agreements/${agreement.id.toString}/activate", HttpMethods.PATCH))
      .to[Agreement]
  } yield activated

  def suspendAgreement(
    agreement: Agreement
  )(implicit ec: ExecutionContext, actorSystem: actor.ActorSystem): Future[Agreement] = for {
    suspended <- Unmarshal(makeRequest(emptyData, s"agreements/${agreement.id.toString}/suspend", HttpMethods.PATCH))
      .to[Agreement]
  } yield suspended

  def prepareDataForListingTests(implicit ec: ExecutionContext, actorSystem: actor.ActorSystem): Unit = {
    (() => mockUUIDSupplier.get).expects().returning(AgreementOne.agreementId).once()
    (() => mockUUIDSupplier.get).expects().returning(AgreementTwo.agreementId).once()
    (() => mockUUIDSupplier.get).expects().returning(AgreementThree.agreementId).once()
    val agreementSeed1 = AgreementSeed(
      eserviceId = AgreementOne.eserviceId,
      producerId = AgreementOne.producerId,
      consumerId = AgreementOne.consumerId,
      verifiedAttributes = Seq(
        VerifiedAttributeSeed(id = Attributes.id1, verified = true, validityTimespan = None),
        VerifiedAttributeSeed(id = Attributes.id2, verified = false, validityTimespan = None),
        VerifiedAttributeSeed(id = Attributes.id3, verified = false, validityTimespan = Some(123L))
      )
    )

    val agreementSeed2 = agreementSeed1.copy(
      eserviceId = AgreementTwo.eserviceId,
      consumerId = AgreementTwo.consumerId,
      producerId = AgreementTwo.producerId,
      verifiedAttributes = Seq(
        VerifiedAttributeSeed(id = Attributes.id1, verified = false, validityTimespan = None),
        VerifiedAttributeSeed(id = Attributes.id2, verified = true, validityTimespan = None),
        VerifiedAttributeSeed(id = Attributes.id3, verified = false, validityTimespan = Some(123L))
      )
    )

    val agreementSeed3 = agreementSeed1.copy(
      eserviceId = AgreementThree.eserviceId,
      consumerId = AgreementThree.consumerId,
      producerId = AgreementThree.producerId,
      verifiedAttributes = Seq(
        VerifiedAttributeSeed(id = Attributes.id1, verified = false, validityTimespan = None),
        VerifiedAttributeSeed(id = Attributes.id2, verified = false, validityTimespan = None),
        VerifiedAttributeSeed(id = Attributes.id3, verified = false, validityTimespan = Some(123L))
      )
    )

    val pending   = createAgreement(agreementSeed1)
    val activated = createAgreement(agreementSeed2).flatMap(activateAgreement)
    val suspended = createAgreement(agreementSeed3).flatMap(suspendAgreement)

    val complete = for {
      _ <- pending
      _ <- activated
      _ <- suspended
    } yield ()

    Await.result(complete, Duration.Inf)
  }
}
