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
    val persistentEntity = Entity(AgreementPersistentBehavior.TypeKey)(behaviorFactory())

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

    "update an agreement properly" in {
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

      val updatedAgreementResponse = submitAgreement(bodyResponse)

      val updatedAgreement = Await.result(updatedAgreementResponse, Duration.Inf)

      updatedAgreement.state shouldBe AgreementState.PENDING
    }

    "add a contract to an agreement" in {
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
        document <- addContract[Document](agreementId, documentId, documentSeed)
      } yield document

      val bodyResponse: Document = response.futureValue

      bodyResponse.id shouldBe documentId
      bodyResponse.name shouldBe documentSeed.name
      bodyResponse.contentType shouldBe documentSeed.contentType
      bodyResponse.path shouldBe documentSeed.path
      bodyResponse.createdAt shouldBe timestamp
    }

    "fail trying to add a contract to an agreement if a contract already exists" in {
      val agreementId  = UUID.randomUUID()
      val eserviceId   = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()
      val producerId   = UUID.randomUUID()
      val consumerId   = UUID.randomUUID()
      val documentId1  = UUID.randomUUID()
      val documentId2  = UUID.randomUUID()

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

      val response: Future[Problem] = for {
        _        <- createAgreement(agreementSeed, agreementId)
        _        <- addContract[Document](agreementId, documentId1, documentSeed)
        document <- addContract[Problem](agreementId, documentId2, documentSeed)
      } yield document

      response.futureValue shouldBe Problem(
        "about:blank",
        409,
        "The request could not be processed because of conflict in the request, such as an edit conflict.",
        None,
        List(ProblemError("004-0006", s"Agreement document for ${agreementId.toString} already exists"))
      )

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
      draft  <- createAgreement(agreementSeed, agreementId)
      active <- activateAgreement(draft)
    } yield active

    val bodyResponse: Agreement = Await.result(response, Duration.Inf)
    bodyResponse.verifiedAttributes shouldBe empty
    bodyResponse.certifiedAttributes shouldBe empty
    bodyResponse.declaredAttributes shouldBe empty
    bodyResponse.state shouldBe AgreementState.ACTIVE

    // and its upgrade
    val upgradedAgreementId = UUID.randomUUID()
    val _ = upgradeAgreement(agreementId.toString, upgradedAgreementId, upgradeAgreementSeed).futureValue

    // when we retrieve the original agreement. it should have its state changed to "inactive"
    val archivedAgreement = getAgreement(agreementId.toString).futureValue
    archivedAgreement.state shouldBe AgreementState.ARCHIVED

    // when we retrieve the updated agreement, it should have its state changed to "active"
    val activeAgreement = getAgreement(upgradedAgreementId.toString).futureValue

    activeAgreement.state shouldBe AgreementState.ACTIVE
  }

}
