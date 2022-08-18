package it.pagopa.interop.agreementmanagement.provider

import akka.actor
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Join}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import it.pagopa.interop.agreementmanagement._
import it.pagopa.interop.agreementmanagement.api.AgreementApi
import it.pagopa.interop.agreementmanagement.api.impl.{AgreementApiMarshallerImpl, AgreementApiServiceImpl}
import it.pagopa.interop.agreementmanagement.model._
import it.pagopa.interop.agreementmanagement.model.persistence.AgreementPersistentBehavior
import it.pagopa.interop.agreementmanagement.server.Controller
import it.pagopa.interop.agreementmanagement.server.impl.Dependencies
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

/** Local integration test.
  *
  * Starts a local cluster sharding and invokes REST operations on the eventsourcing entity
  */
class AgreementApiServiceSpec
    extends ScalaTestWithActorTestKit(SpecConfiguration.config)
    with Dependencies
    with AnyWordSpecLike
    with SpecConfiguration
    with SpecHelper {

  var controller: Option[Controller]                                    = None
  var bindServer: Option[Future[Http.ServerBinding]]                    = None
  val wrappingDirective: AuthenticationDirective[Seq[(String, String)]] =
    SecurityDirectives.authenticateOAuth2("SecurityRealm", AdminMockAuthenticator)

  val sharding: ClusterSharding = ClusterSharding(system)

  val httpSystem: ActorSystem[Any] =
    ActorSystem(Behaviors.ignore[Any], name = system.name, config = system.settings.config)

  implicit val executionContext: ExecutionContextExecutor = httpSystem.executionContext
  implicit val classicSystem: actor.ActorSystem           = httpSystem.classicSystem

  override def beforeAll(): Unit = {
    val persistentEntity = Entity(AgreementPersistentBehavior.TypeKey)(behaviorFactory)

    Cluster(system).manager ! Join(Cluster(system).selfMember.address)
    sharding.init(persistentEntity)

    val agreementApi = new AgreementApi(
      AgreementApiServiceImpl(system, sharding, persistentEntity, mockUUIDSupplier, mockDateTimeSupplier),
      AgreementApiMarshallerImpl,
      wrappingDirective
    )

    controller = Some(new Controller(agreementApi)(classicSystem))

    controller foreach { controller =>
      bindServer = Some(
        Http()
          .newServerAt("0.0.0.0", 18088)
          .bind(controller.routes)
      )

      Await.result(bindServer.get, 100.seconds)
    }
  }

  override def afterAll(): Unit = {
    bindServer.foreach(_.foreach(_.unbind()))
    ActorTestKit.shutdown(httpSystem, 5.seconds)
    super.afterAll()
  }

  "Processing a request payload" must {

    "create a new agreement" in {
      val agreementId  = UUID.fromString("4330c05c-e892-4fed-a664-c40f27b50e63")
      val eserviceId   = UUID.fromString("8c4da4ab-8dee-4af1-be5e-c93addb48d6d")
      val descriptorId = UUID.fromString("ac51f56f-9944-4f96-a8f1-93cd0c256ac7")
      val producerId   = UUID.fromString("5cc978b5-87f9-4685-8be1-a11e6e795964")
      val consumerId   = UUID.fromString("cbc42433-2165-40a5-a60d-bb33471e8a91")

      val attributeId1 = UUID.fromString("9888fb13-870b-45c6-9cd8-b74457bb797a")
      val attributeId2 = UUID.fromString("8141a60d-5fd8-45bb-b229-ad661a6793ba")
      val attributeId3 = UUID.fromString("f59b912c-6a5e-451c-8041-71b1c2087250")

      val agreementSeed = AgreementSeed(
        eserviceId = eserviceId,
        descriptorId = descriptorId,
        producerId = producerId,
        consumerId = consumerId,
        verifiedAttributes = Seq(AttributeSeed(id = attributeId1)),
        certifiedAttributes = Seq(AttributeSeed(id = attributeId2)),
        declaredAttributes = Seq(AttributeSeed(id = attributeId3))
      )

      val response: Future[Agreement] = createAgreement(agreementSeed, agreementId)

      val bodyResponse: Agreement = Await.result(response, Duration.Inf)

      bodyResponse.id shouldBe agreementId
      bodyResponse.state shouldBe AgreementState.PENDING
      bodyResponse.verifiedAttributes.find(p => p.id == attributeId1) shouldBe a[Some[_]]
      bodyResponse.certifiedAttributes.find(p => p.id == attributeId2) shouldBe a[Some[_]]
      bodyResponse.declaredAttributes.find(p => p.id == attributeId3) shouldBe a[Some[_]]
    }

    "activate an agreement properly" in {
      // given a pending agreement
      val agreementId = UUID.fromString("d1040ff3-7fbc-4b29-9081-b1b53f72d386")

      val agreementSeed = AgreementSeed(
        eserviceId = UUID.fromString("94c06558-c0f9-42e0-a11e-c5f82e4b8a9b"),
        descriptorId = UUID.fromString("77262ddf-7743-4f26-85fd-ec53febbfe56"),
        producerId = UUID.fromString("f318fcc9-5421-4081-8e13-ba508152af9e"),
        consumerId = UUID.fromString("1f538520-95b6-4d3f-accf-90c7b183df9f"),
        verifiedAttributes = Seq.empty,
        certifiedAttributes = Seq.empty,
        declaredAttributes = Seq.empty
      )

      val response: Future[Agreement] = createAgreement(agreementSeed, agreementId)

      val bodyResponse: Agreement = Await.result(response, Duration.Inf)
      bodyResponse.verifiedAttributes shouldBe empty
      bodyResponse.state shouldBe AgreementState.PENDING

      // when the activation occurs
      val activateAgreementResponse = activateAgreement(bodyResponse)

      // the agreement should change its status to "active"
      val activatedAgreement = Await.result(activateAgreementResponse, Duration.Inf)

      activatedAgreement.state shouldBe AgreementState.ACTIVE
    }

    "suspend an agreement properly, changed by consumer" in {
      // given a pending agreement
      val agreementId = UUID.randomUUID()

      val agreementSeed = AgreementSeed(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        verifiedAttributes = Seq.empty,
        certifiedAttributes = Seq.empty,
        declaredAttributes = Seq.empty
      )

      val response: Future[Agreement] = for {
        bodyResponse              <- createAgreement(agreementSeed, agreementId)
        activateAgreementResponse <- activateAgreement(bodyResponse)
        suspendByConsumer         <- suspendAgreement(activateAgreementResponse, Some(ChangedBy.CONSUMER))
      } yield suspendByConsumer

      val bodyResponse: Agreement = Await.result(response, Duration.Inf)

      bodyResponse.state shouldBe AgreementState.SUSPENDED
      bodyResponse.suspendedByConsumer shouldBe Some(true)
      bodyResponse.suspendedByProducer shouldBe None

    }

    "suspend an agreement properly, changed by producer" in {
      // given a pending agreement
      val agreementId = UUID.randomUUID()

      val agreementSeed = AgreementSeed(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        verifiedAttributes = Seq.empty,
        certifiedAttributes = Seq.empty,
        declaredAttributes = Seq.empty
      )

      val response: Future[Agreement] = for {
        bodyResponse              <- createAgreement(agreementSeed, agreementId)
        activateAgreementResponse <- activateAgreement(bodyResponse, Some(ChangedBy.PRODUCER))
        suspendByProducer         <- suspendAgreement(activateAgreementResponse, Some(ChangedBy.PRODUCER))
      } yield suspendByProducer

      val bodyResponse: Agreement = Await.result(response, Duration.Inf)

      bodyResponse.state shouldBe AgreementState.SUSPENDED
      bodyResponse.suspendedByConsumer shouldBe None
      bodyResponse.suspendedByProducer shouldBe Some(true)

    }

    "suspend an agreement properly, changed by platform" in {
      // given a pending agreement
      val agreementId = UUID.randomUUID()

      val agreementSeed = AgreementSeed(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        verifiedAttributes = Seq.empty,
        certifiedAttributes = Seq.empty,
        declaredAttributes = Seq.empty
      )

      val response: Future[Agreement] = for {
        bodyResponse              <- createAgreement(agreementSeed, agreementId)
        activateAgreementResponse <- activateAgreement(bodyResponse, Some(ChangedBy.PRODUCER))
        suspendByPlatform         <- suspendAgreement(activateAgreementResponse, Some(ChangedBy.PLATFORM))
      } yield suspendByPlatform

      val bodyResponse: Agreement = Await.result(response, Duration.Inf)

      bodyResponse.state shouldBe AgreementState.SUSPENDED
      bodyResponse.suspendedByConsumer shouldBe None
      bodyResponse.suspendedByProducer shouldBe Some(false)
      bodyResponse.suspendedByPlatform shouldBe Some(true)

    }

    "suspend an agreement properly, changed by consumer and producer" in {
      // given a pending agreement
      val agreementId = UUID.randomUUID()

      val agreementSeed = AgreementSeed(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        verifiedAttributes = Seq.empty,
        certifiedAttributes = Seq.empty,
        declaredAttributes = Seq.empty
      )

      val response: Future[Agreement] = for {
        bodyResponse              <- createAgreement(agreementSeed, agreementId)
        activateAgreementResponse <- activateAgreement(bodyResponse)
        suspendByConsumer         <- suspendAgreement(activateAgreementResponse, Some(ChangedBy.CONSUMER))
        suspendByProducer         <- suspendAgreement(suspendByConsumer, Some(ChangedBy.PRODUCER))
      } yield suspendByProducer

      val bodyResponse: Agreement = Await.result(response, Duration.Inf)

      bodyResponse.state shouldBe AgreementState.SUSPENDED
      bodyResponse.suspendedByConsumer shouldBe Some(true)
      bodyResponse.suspendedByProducer shouldBe Some(true)

    }

    "suspend an agreement properly, changed by consumer and platform" in {
      // given a pending agreement
      val agreementId = UUID.randomUUID()

      val agreementSeed = AgreementSeed(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        verifiedAttributes = Seq.empty,
        certifiedAttributes = Seq.empty,
        declaredAttributes = Seq.empty
      )

      val response: Future[Agreement] = for {
        bodyResponse              <- createAgreement(agreementSeed, agreementId)
        activateAgreementResponse <- activateAgreement(bodyResponse)
        suspendByConsumer         <- suspendAgreement(activateAgreementResponse, Some(ChangedBy.CONSUMER))
        suspendByPlatform         <- suspendAgreement(suspendByConsumer, Some(ChangedBy.PLATFORM))
      } yield suspendByPlatform

      val bodyResponse: Agreement = Await.result(response, Duration.Inf)

      bodyResponse.state shouldBe AgreementState.SUSPENDED
      bodyResponse.suspendedByConsumer shouldBe Some(true)
      bodyResponse.suspendedByProducer shouldBe None
      bodyResponse.suspendedByPlatform shouldBe Some(true)
    }

    "activate an agreement (suspended by consumer - activated by consumer)" in {
      // given a pending agreement
      val agreementId = UUID.randomUUID()

      val agreementSeed = AgreementSeed(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        verifiedAttributes = Seq.empty,
        certifiedAttributes = Seq.empty,
        declaredAttributes = Seq.empty
      )

      val response: Future[Agreement] = for {
        bodyResponse <- createAgreement(agreementSeed, agreementId)
        activated    <- activateAgreement(bodyResponse, Some(ChangedBy.CONSUMER))
        suspended    <- suspendAgreement(activated, Some(ChangedBy.CONSUMER))
        reActivated  <- activateAgreement(suspended, Some(ChangedBy.CONSUMER))
      } yield reActivated

      val bodyResponse: Agreement = Await.result(response, Duration.Inf)

      bodyResponse.state shouldBe AgreementState.ACTIVE
      bodyResponse.suspendedByConsumer shouldBe Some(false)
      bodyResponse.suspendedByProducer shouldBe None

    }

    "activate an agreement (suspended by producer - activated by producer)" in {
      // given a pending agreement
      val agreementId = UUID.randomUUID()

      val agreementSeed = AgreementSeed(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        verifiedAttributes = Seq.empty,
        certifiedAttributes = Seq.empty,
        declaredAttributes = Seq.empty
      )

      val response: Future[Agreement] = for {
        bodyResponse <- createAgreement(agreementSeed, agreementId)
        activated    <- activateAgreement(bodyResponse, Some(ChangedBy.PRODUCER))
        suspended    <- suspendAgreement(activated, Some(ChangedBy.PRODUCER))
        reActivated  <- activateAgreement(suspended, Some(ChangedBy.PRODUCER))
      } yield reActivated

      val bodyResponse: Agreement = Await.result(response, Duration.Inf)

      bodyResponse.state shouldBe AgreementState.ACTIVE

    }

    "activate an agreement (suspended by platform - activated by platform)" in {
      // given a pending agreement
      val agreementId = UUID.randomUUID()

      val agreementSeed = AgreementSeed(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        verifiedAttributes = Seq.empty,
        certifiedAttributes = Seq.empty,
        declaredAttributes = Seq.empty
      )

      val response: Future[Agreement] = for {
        bodyResponse <- createAgreement(agreementSeed, agreementId)
        activated    <- activateAgreement(bodyResponse, Some(ChangedBy.PRODUCER))
        suspended    <- suspendAgreement(activated, Some(ChangedBy.PLATFORM))
        reActivated  <- activateAgreement(suspended, Some(ChangedBy.PLATFORM))
      } yield reActivated

      val bodyResponse: Agreement = Await.result(response, Duration.Inf)

      bodyResponse.state shouldBe AgreementState.ACTIVE
      bodyResponse.suspendedByPlatform shouldBe Some(false)
    }

    "remain suspended (suspended by producer - activated by consumer)" in {
      // given a pending agreement
      val agreementId = UUID.randomUUID()

      val agreementSeed = AgreementSeed(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        verifiedAttributes = Seq.empty,
        certifiedAttributes = Seq.empty,
        declaredAttributes = Seq.empty
      )

      val response: Future[Agreement] = for {
        bodyResponse          <- createAgreement(agreementSeed, agreementId)
        activated             <- activateAgreement(bodyResponse, Some(ChangedBy.CONSUMER))
        suspendedByConsumer   <- suspendAgreement(activated, Some(ChangedBy.CONSUMER))
        suspendedByProducer   <- suspendAgreement(suspendedByConsumer, Some(ChangedBy.PRODUCER))
        reActivatedByConsumer <- activateAgreement(suspendedByProducer, Some(ChangedBy.CONSUMER))
      } yield reActivatedByConsumer

      val bodyResponse: Agreement = Await.result(response, Duration.Inf)

      bodyResponse.state shouldBe AgreementState.SUSPENDED
      bodyResponse.suspendedByConsumer shouldBe Some(false)
      bodyResponse.suspendedByProducer shouldBe Some(true)

    }

    "remain suspended (suspended by consumer - activated by producer)" in {
      // given a pending agreement
      val agreementId = UUID.randomUUID()

      val agreementSeed = AgreementSeed(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        verifiedAttributes = Seq.empty,
        certifiedAttributes = Seq.empty,
        declaredAttributes = Seq.empty
      )

      val response: Future[Agreement] = for {
        bodyResponse          <- createAgreement(agreementSeed, agreementId)
        activated             <- activateAgreement(bodyResponse, Some(ChangedBy.PRODUCER))
        suspendedByProducer   <- suspendAgreement(activated, Some(ChangedBy.PRODUCER))
        suspendedByConsumer   <- suspendAgreement(suspendedByProducer, Some(ChangedBy.CONSUMER))
        reActivatedByProducer <- activateAgreement(suspendedByConsumer, Some(ChangedBy.PRODUCER))
      } yield reActivatedByProducer

      val bodyResponse: Agreement = Await.result(response, Duration.Inf)

      bodyResponse.state shouldBe AgreementState.SUSPENDED
      bodyResponse.suspendedByConsumer shouldBe Some(true)
      bodyResponse.suspendedByProducer shouldBe Some(false)

    }

    "remain suspended (suspended by consumer - activated by platform)" in {
      // given a pending agreement
      val agreementId = UUID.randomUUID()

      val agreementSeed = AgreementSeed(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        verifiedAttributes = Seq.empty,
        certifiedAttributes = Seq.empty,
        declaredAttributes = Seq.empty
      )

      val response: Future[Agreement] = for {
        bodyResponse          <- createAgreement(agreementSeed, agreementId)
        activated             <- activateAgreement(bodyResponse, Some(ChangedBy.PRODUCER))
        suspendedByPlatform   <- suspendAgreement(activated, Some(ChangedBy.PLATFORM))
        suspendedByConsumer   <- suspendAgreement(suspendedByPlatform, Some(ChangedBy.CONSUMER))
        reActivatedByPlatform <- activateAgreement(suspendedByConsumer, Some(ChangedBy.PLATFORM))
      } yield reActivatedByPlatform

      val bodyResponse: Agreement = Await.result(response, Duration.Inf)

      bodyResponse.state shouldBe AgreementState.SUSPENDED
      bodyResponse.suspendedByConsumer shouldBe Some(true)
      bodyResponse.suspendedByProducer shouldBe Some(false)
      bodyResponse.suspendedByProducer shouldBe Some(false)
    }

    "activate an agreement properly (suspended by producer and consumer - activated by producer and consumer)" in {
      // given a pending agreement
      val agreementId = UUID.randomUUID()

      val agreementSeed = AgreementSeed(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        verifiedAttributes = Seq.empty,
        certifiedAttributes = Seq.empty,
        declaredAttributes = Seq.empty
      )

      val response: Future[Agreement] = for {
        bodyResponse          <- createAgreement(agreementSeed, agreementId)
        activated             <- activateAgreement(bodyResponse)
        suspendedByProducer   <- suspendAgreement(activated, Some(ChangedBy.PRODUCER))
        suspendedByConsumer   <- suspendAgreement(suspendedByProducer, Some(ChangedBy.CONSUMER))
        reActivatedByProducer <- activateAgreement(suspendedByConsumer, Some(ChangedBy.PRODUCER))
        reActivatedByConsumer <- activateAgreement(reActivatedByProducer, Some(ChangedBy.CONSUMER))
      } yield reActivatedByConsumer

      val bodyResponse: Agreement = Await.result(response, Duration.Inf)

      bodyResponse.state shouldBe AgreementState.ACTIVE
      bodyResponse.suspendedByConsumer shouldBe Some(false)
      bodyResponse.suspendedByProducer shouldBe Some(false)

    }

    "activate an agreement properly (suspended by producer, consumer and platform - activated by producer, consumer and platform)" in {
      // given a pending agreement
      val agreementId = UUID.randomUUID()

      val agreementSeed = AgreementSeed(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        verifiedAttributes = Seq.empty,
        certifiedAttributes = Seq.empty,
        declaredAttributes = Seq.empty
      )

      val response: Future[Agreement] = for {
        bodyResponse          <- createAgreement(agreementSeed, agreementId)
        activated             <- activateAgreement(bodyResponse)
        suspendedByProducer   <- suspendAgreement(activated, Some(ChangedBy.PRODUCER))
        suspendedByConsumer   <- suspendAgreement(suspendedByProducer, Some(ChangedBy.CONSUMER))
        suspendedByPlatform   <- suspendAgreement(suspendedByConsumer, Some(ChangedBy.PLATFORM))
        reActivatedByProducer <- activateAgreement(suspendedByPlatform, Some(ChangedBy.PRODUCER))
        reActivatedByConsumer <- activateAgreement(reActivatedByProducer, Some(ChangedBy.CONSUMER))
        reActivatedByPlatform <- activateAgreement(reActivatedByConsumer, Some(ChangedBy.PLATFORM))
      } yield reActivatedByPlatform

      val bodyResponse: Agreement = Await.result(response, Duration.Inf)

      bodyResponse.state shouldBe AgreementState.ACTIVE
      bodyResponse.suspendedByConsumer shouldBe Some(false)
      bodyResponse.suspendedByProducer shouldBe Some(false)
      bodyResponse.suspendedByPlatform shouldBe Some(false)
    }

    "add a document to a verified attribute" in {
      val agreementId  = UUID.randomUUID()
      val eserviceId   = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()
      val producerId   = UUID.randomUUID()
      val consumerId   = UUID.randomUUID()
      val documentId   = UUID.randomUUID()

      val attributeId1 = UUID.randomUUID()
      val attributeId2 = UUID.randomUUID()
      val attributeId3 = UUID.randomUUID()

      val agreementSeed = AgreementSeed(
        eserviceId = eserviceId,
        descriptorId = descriptorId,
        producerId = producerId,
        consumerId = consumerId,
        verifiedAttributes = Seq(AttributeSeed(id = attributeId1)),
        certifiedAttributes = Seq(AttributeSeed(id = attributeId2)),
        declaredAttributes = Seq(AttributeSeed(id = attributeId3))
      )

      val documentSeed = DocumentSeed(name = "doc1", contentType = "pdf", path = "somewhere")

      val response: Future[Document] = for {
        _        <- createAgreement(agreementSeed, agreementId)
        document <- addDocument(agreementId, attributeId1, documentId, documentSeed)
      } yield document

      val bodyResponse: Document = response.futureValue

      bodyResponse.id shouldBe documentId
      bodyResponse.name shouldBe documentSeed.name
      bodyResponse.contentType shouldBe documentSeed.contentType
      bodyResponse.path shouldBe documentSeed.path
      bodyResponse.createdAt shouldBe timestamp
    }

    "remove a document from a verified attribute" in {
      val agreementId  = UUID.randomUUID()
      val eserviceId   = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()
      val producerId   = UUID.randomUUID()
      val consumerId   = UUID.randomUUID()
      val documentId   = UUID.randomUUID()

      val attributeId1 = UUID.randomUUID()
      val attributeId2 = UUID.randomUUID()
      val attributeId3 = UUID.randomUUID()

      val agreementSeed = AgreementSeed(
        eserviceId = eserviceId,
        descriptorId = descriptorId,
        producerId = producerId,
        consumerId = consumerId,
        verifiedAttributes = Seq(AttributeSeed(id = attributeId1)),
        certifiedAttributes = Seq(AttributeSeed(id = attributeId2)),
        declaredAttributes = Seq(AttributeSeed(id = attributeId3))
      )

      val documentSeed = DocumentSeed(name = "doc1", contentType = "pdf", path = "somewhere")

      val response: Future[String] = for {
        _        <- createAgreement(agreementSeed, agreementId)
        _        <- addDocument(agreementId, attributeId1, documentId, documentSeed)
        response <- removeDocument(agreementId, attributeId1, documentId)
      } yield response

      val bodyResponse: String = response.futureValue

      bodyResponse shouldBe empty
    }

  }

  "upgrade an agreement properly" in {
    // given a pending agreement
    val agreementId = UUID.randomUUID()

    val agreementSeed        = AgreementSeed(
      eserviceId = UUID.fromString("94c06558-c0f9-42e0-a11e-c5f82e4b8a9b"),
      descriptorId = UUID.fromString("77262ddf-7743-4f26-85fd-ec53febbfe56"),
      producerId = UUID.fromString("f318fcc9-5421-4081-8e13-ba508152af9e"),
      consumerId = UUID.fromString("1f538520-95b6-4d3f-accf-90c7b183df9f"),
      verifiedAttributes = Seq.empty,
      certifiedAttributes = Seq.empty,
      declaredAttributes = Seq.empty
    )
//    (() => mockUUIDSupplier.get).expects().returning(agreementId).once()
    val upgradeAgreementSeed = UpgradeAgreementSeed(descriptorId = UUID.randomUUID())

    val response: Future[Agreement] = createAgreement(agreementSeed, agreementId)
    val bodyResponse: Agreement     = Await.result(response, Duration.Inf)
    bodyResponse.verifiedAttributes shouldBe empty
    bodyResponse.certifiedAttributes shouldBe empty
    bodyResponse.declaredAttributes shouldBe empty
    bodyResponse.state shouldBe AgreementState.PENDING

    // after its activation
    val _ = activateAgreement(bodyResponse).futureValue

    // and its upgrade
    val updateAgreementId = UUID.randomUUID()
//    (() => mockUUIDSupplier.get).expects().returning(updateAgreementId).once()
    val _                 = upgradeAgreement(agreementId.toString, updateAgreementId, upgradeAgreementSeed).futureValue

    // when we retrieve the original agreement. it should have its state changed to "inactive"
    val inactiveAgreement = getAgreement(agreementId.toString).futureValue
    inactiveAgreement.state shouldBe AgreementState.INACTIVE

    // when we retrieve the updated agreement, it should have its state changed to "active"
    val activeAgreement = getAgreement(updateAgreementId.toString).futureValue
    activeAgreement.state shouldBe AgreementState.ACTIVE
  }

}
