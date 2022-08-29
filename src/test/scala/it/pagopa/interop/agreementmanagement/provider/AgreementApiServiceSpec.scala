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
      val agreementId  = UUID.randomUUID()
      val eserviceId   = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()
      val producerId   = UUID.randomUUID()
      val consumerId   = UUID.randomUUID()

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

      val response: Future[Agreement] = createAgreement(agreementSeed, agreementId)

      val bodyResponse: Agreement = Await.result(response, Duration.Inf)

      bodyResponse.id shouldBe agreementId
      bodyResponse.state shouldBe AgreementState.DRAFT
      bodyResponse.verifiedAttributes.find(p => p.id == attributeId1) shouldBe a[Some[_]]
      bodyResponse.certifiedAttributes.find(p => p.id == attributeId2) shouldBe a[Some[_]]
      bodyResponse.declaredAttributes.find(p => p.id == attributeId3) shouldBe a[Some[_]]
    }

    "submit an agreement properly" in {
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

      val response: Future[Agreement] = createAgreement(agreementSeed, agreementId)

      val bodyResponse: Agreement = Await.result(response, Duration.Inf)
      bodyResponse.verifiedAttributes shouldBe empty
      bodyResponse.state shouldBe AgreementState.DRAFT

      // when the activation occurs
      val submitAgreementResponse = submitAgreement(bodyResponse)

      // the agreement should change its status to "active"
      val submittedAgreement = Await.result(submitAgreementResponse, Duration.Inf)

      submittedAgreement.state shouldBe AgreementState.PENDING
    }

    "activate an agreement properly" in {
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
        draft   <- createAgreement(agreementSeed, agreementId)
        pending <- submitAgreement(draft)
      } yield pending

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
        draft             <- createAgreement(agreementSeed, agreementId)
        pending           <- submitAgreement(draft)
        active            <- activateAgreement(pending)
        suspendByConsumer <- suspendAgreement(active, suspendedByConsumer = Some(true))
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
        draft             <- createAgreement(agreementSeed, agreementId)
        pending           <- submitAgreement(draft)
        active            <- activateAgreement(pending)
        suspendByProducer <- suspendAgreement(active, suspendedByProducer = Some(true))
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
        draft             <- createAgreement(agreementSeed, agreementId)
        pending           <- submitAgreement(draft)
        activate          <- activateAgreement(pending)
        suspendByPlatform <- suspendAgreement(activate, suspendedByPlatform = Some(true))
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
        draft             <- createAgreement(agreementSeed, agreementId)
        pending           <- submitAgreement(draft)
        activate          <- activateAgreement(pending)
        suspendByConsumer <- suspendAgreement(activate, suspendedByConsumer = Some(true))
        suspendByProducer <- suspendAgreement(suspendByConsumer, suspendedByProducer = Some(true))
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
        draft             <- createAgreement(agreementSeed, agreementId)
        pending           <- submitAgreement(draft)
        active            <- activateAgreement(pending)
        suspendByConsumer <- suspendAgreement(active, suspendedByConsumer = Some(true))
        suspendByPlatform <- suspendAgreement(suspendByConsumer, suspendedByPlatform = Some(true))
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
        draft       <- createAgreement(agreementSeed, agreementId)
        pending     <- submitAgreement(draft)
        active      <- activateAgreement(pending)
        suspended   <- suspendAgreement(active, suspendedByConsumer = Some(true))
        reActivated <- activateAgreement(suspended)
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
        draft       <- createAgreement(agreementSeed, agreementId)
        pending     <- submitAgreement(draft)
        active      <- activateAgreement(pending)
        suspended   <- suspendAgreement(active, suspendedByProducer = Some(true))
        reActivated <- activateAgreement(suspended)
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
        draft       <- createAgreement(agreementSeed, agreementId)
        pending     <- submitAgreement(draft)
        active      <- activateAgreement(pending)
        suspended   <- suspendAgreement(active, suspendedByProducer = Some(true))
        reActivated <- activateAgreement(suspended)
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
        draft                 <- createAgreement(agreementSeed, agreementId)
        pending               <- submitAgreement(draft)
        active                <- activateAgreement(pending)
        suspendedByConsumer   <- suspendAgreement(active, suspendedByConsumer = Some(true))
        suspendedByProducer   <- suspendAgreement(suspendedByConsumer, suspendedByProducer = Some(true))
        reActivatedByConsumer <- activateAgreement(suspendedByProducer)
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
        draft                 <- createAgreement(agreementSeed, agreementId)
        pending               <- submitAgreement(draft)
        active                <- activateAgreement(pending)
        suspendedByProducer   <- suspendAgreement(active, suspendedByProducer = Some(true))
        suspendedByConsumer   <- suspendAgreement(suspendedByProducer, suspendedByConsumer = Some(true))
        reActivatedByProducer <- activateAgreement(suspendedByConsumer)
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
        draft                 <- createAgreement(agreementSeed, agreementId)
        pending               <- submitAgreement(draft)
        active                <- activateAgreement(pending)
        suspendedByPlatform   <- suspendAgreement(active, suspendedByPlatform = Some(true))
        suspendedByConsumer   <- suspendAgreement(suspendedByPlatform, suspendedByConsumer = Some(true))
        reActivatedByPlatform <- activateAgreement(suspendedByConsumer)
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
        draft                 <- createAgreement(agreementSeed, agreementId)
        pending               <- submitAgreement(draft)
        active                <- activateAgreement(pending)
        suspendedByProducer   <- suspendAgreement(active, suspendedByProducer = Some(true))
        suspendedByConsumer   <- suspendAgreement(suspendedByProducer, suspendedByConsumer = Some(true))
        reActivatedByProducer <- activateAgreement(suspendedByConsumer)
        reActivatedByConsumer <- activateAgreement(reActivatedByProducer)
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
        draft                 <- createAgreement(agreementSeed, agreementId)
        pending               <- submitAgreement(draft)
        active                <- activateAgreement(pending)
        suspendedByProducer   <- suspendAgreement(active, suspendedByProducer = Some(true))
        suspendedByConsumer   <- suspendAgreement(suspendedByProducer, suspendedByConsumer = Some(true))
        suspendedByPlatform   <- suspendAgreement(suspendedByConsumer, suspendedByPlatform = Some(true))
        reActivatedByProducer <- activateAgreement(suspendedByPlatform)
        reActivatedByConsumer <- activateAgreement(reActivatedByProducer)
        reActivatedByPlatform <- activateAgreement(reActivatedByConsumer)
      } yield reActivatedByPlatform

      val bodyResponse: Agreement = Await.result(response, Duration.Inf)

      bodyResponse.state shouldBe AgreementState.ACTIVE
      bodyResponse.suspendedByConsumer shouldBe Some(false)
      bodyResponse.suspendedByProducer shouldBe Some(false)
      bodyResponse.suspendedByPlatform shouldBe Some(false)
    }

    "add a consumer document to an agreement" in {
      val agreementId  = UUID.randomUUID()
      val eserviceId   = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()
      val producerId   = UUID.randomUUID()
      val consumerId   = UUID.randomUUID()
      val documentId   = UUID.randomUUID()

      val agreementSeed = AgreementSeed(
        eserviceId = eserviceId,
        descriptorId = descriptorId,
        producerId = producerId,
        consumerId = consumerId,
        verifiedAttributes = Nil,
        certifiedAttributes = Nil,
        declaredAttributes = Nil
      )

      val documentSeed = DocumentSeed(name = "doc1", prettyName = "prettyDoc1", contentType = "pdf", path = "somewhere")

      val response: Future[Document] = for {
        _        <- createAgreement(agreementSeed, agreementId)
        document <- addConsumerDocument(agreementId, documentId, documentSeed)
      } yield document

      val bodyResponse: Document = response.futureValue

      bodyResponse.id shouldBe documentId
      bodyResponse.name shouldBe documentSeed.name
      bodyResponse.contentType shouldBe documentSeed.contentType
      bodyResponse.path shouldBe documentSeed.path
      bodyResponse.createdAt shouldBe timestamp
    }

    "remove a consumer document from an agreement" in {
      val agreementId  = UUID.randomUUID()
      val eserviceId   = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()
      val producerId   = UUID.randomUUID()
      val consumerId   = UUID.randomUUID()
      val documentId   = UUID.randomUUID()

      val agreementSeed = AgreementSeed(
        eserviceId = eserviceId,
        descriptorId = descriptorId,
        producerId = producerId,
        consumerId = consumerId,
        verifiedAttributes = Nil,
        certifiedAttributes = Nil,
        declaredAttributes = Nil
      )

      val documentSeed = DocumentSeed(name = "doc1", prettyName = "prettyDoc1", contentType = "pdf", path = "somewhere")

      val response: Future[String] = for {
        _        <- createAgreement(agreementSeed, agreementId)
        _        <- addConsumerDocument(agreementId, documentId, documentSeed)
        response <- removeConsumerDocument(agreementId, documentId)
      } yield response

      val bodyResponse: String = response.futureValue

      bodyResponse shouldBe empty
    }

  }

  "upgrade an agreement properly" in {
    // given a pending agreement
    val agreementId = UUID.randomUUID()

    val agreementSeed        = AgreementSeed(
      eserviceId = UUID.randomUUID(),
      descriptorId = UUID.randomUUID(),
      producerId = UUID.randomUUID(),
      consumerId = UUID.randomUUID(),
      verifiedAttributes = Seq.empty,
      certifiedAttributes = Seq.empty,
      declaredAttributes = Seq.empty
    )
    val upgradeAgreementSeed = UpgradeAgreementSeed(descriptorId = UUID.randomUUID())

    val response: Future[Agreement] = for {
      draft   <- createAgreement(agreementSeed, agreementId)
      pending <- submitAgreement(draft)
    } yield pending

    val bodyResponse: Agreement = Await.result(response, Duration.Inf)
    bodyResponse.verifiedAttributes shouldBe empty
    bodyResponse.certifiedAttributes shouldBe empty
    bodyResponse.declaredAttributes shouldBe empty
    bodyResponse.state shouldBe AgreementState.PENDING

    // after its activation
    val _ = activateAgreement(bodyResponse).futureValue

    // and its upgrade
    val updateAgreementId = UUID.randomUUID()
    val _                 = upgradeAgreement(agreementId.toString, updateAgreementId, upgradeAgreementSeed).futureValue

    // when we retrieve the original agreement. it should have its state changed to "inactive"
    val inactiveAgreement = getAgreement(agreementId.toString).futureValue
    inactiveAgreement.state shouldBe AgreementState.INACTIVE

    // when we retrieve the updated agreement, it should have its state changed to "active"
    val activeAgreement = getAgreement(updateAgreementId.toString).futureValue
    activeAgreement.state shouldBe AgreementState.ACTIVE
  }

}
