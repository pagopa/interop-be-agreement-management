package it.pagopa.pdnd.interop.uservice.agreementmanagement.provider

import akka.actor
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.typed.{Cluster, Join}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpMethods, MessageEntity, StatusCodes}
import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import akka.http.scaladsl.unmarshalling.Unmarshal
import it.pagopa.pdnd.interop.uservice.agreementmanagement._
import it.pagopa.pdnd.interop.uservice.agreementmanagement.api.impl.{
  AgreementApiMarshallerImpl,
  AgreementApiServiceImpl
}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.api.{AgreementApi, AgreementApiMarshaller}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.common.system.Authenticator
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.{Agreement, AgreementSeed, VerifiedAttributeSeed}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.server.Controller
import it.pagopa.pdnd.interop.uservice.agreementmanagement.server.impl.Main
import it.pagopa.pdnd.interop.uservice.agreementmanagement.service.UUIDSupplier
import org.scalamock.scalatest.MockFactory
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

object AgreementApiServiceSpec extends MockFactory {

  val mockUUIDSupplier: UUIDSupplier = mock[UUIDSupplier]
}

/** Local integration test.
  *
  * Starts a local cluster sharding and invokes REST operations on the eventsourcing entity
  */
class AgreementApiServiceSpec
    extends ScalaTestWithActorTestKit(SpecConfiguration.config)
    with AnyWordSpecLike
    with SpecConfiguration
    with SpecHelper {

  val agreementApiMarshaller: AgreementApiMarshaller = new AgreementApiMarshallerImpl
  var controller: Option[Controller]                 = None
  var bindServer: Option[Future[Http.ServerBinding]] = None
  val wrappingDirective: AuthenticationDirective[Seq[(String, String)]] =
    SecurityDirectives.authenticateOAuth2("SecurityRealm", Authenticator)

  val sharding: ClusterSharding = ClusterSharding(system)

  val httpSystem: ActorSystem[Any] =
    ActorSystem(Behaviors.ignore[Any], name = system.name, config = system.settings.config)

  implicit val executionContext: ExecutionContextExecutor = httpSystem.executionContext
  implicit val classicSystem: actor.ActorSystem           = httpSystem.classicSystem

  override def beforeAll(): Unit = {
    val persistentEntity = Main.buildPersistentEntity()

    Cluster(system).manager ! Join(Cluster(system).selfMember.address)
    sharding.init(persistentEntity)

    val agreementApi = new AgreementApi(
      new AgreementApiServiceImpl(system, sharding, persistentEntity, mockUUIDSupplier),
      agreementApiMarshaller,
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
    println("****** Cleaning resources ********")
    bindServer.foreach(_.foreach(_.unbind()))
    ActorTestKit.shutdown(httpSystem, 5.seconds)
    super.afterAll()
    println("Resources cleaned")
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
        verifiedAttributes = Seq(
          VerifiedAttributeSeed(id = attributeId1, verified = Some(true), validityTimespan = None),
          VerifiedAttributeSeed(id = attributeId2, verified = None, validityTimespan = None),
          VerifiedAttributeSeed(id = attributeId3, verified = Some(false), validityTimespan = Some(123L))
        )
      )
      (() => mockUUIDSupplier.get).expects().returning(agreementId).once()

      val response: Future[Agreement] = createAgreement(agreementSeed)

      val bodyResponse: Agreement = Await.result(response, Duration.Inf)

      bodyResponse.id shouldBe agreementId
      bodyResponse.status shouldBe "pending"
      bodyResponse.verifiedAttributes
        .find(p => p.id == attributeId1)
        .get
        .verificationDate shouldBe a[Some[_]]

      bodyResponse.verifiedAttributes
        .find(p => p.id == attributeId2)
        .get
        .verificationDate should be(None)

      bodyResponse.verifiedAttributes
        .find(p => p.id == attributeId3)
        .get
        .validityTimespan
        .get should be(123L)
    }

    "activate an agreement properly" in {
      //given a pending agreement
      val agreementId = UUID.fromString("d1040ff3-7fbc-4b29-9081-b1b53f72d386")

      val agreementSeed = AgreementSeed(
        eserviceId = UUID.fromString("94c06558-c0f9-42e0-a11e-c5f82e4b8a9b"),
        descriptorId = UUID.fromString("77262ddf-7743-4f26-85fd-ec53febbfe56"),
        producerId = UUID.fromString("f318fcc9-5421-4081-8e13-ba508152af9e"),
        consumerId = UUID.fromString("1f538520-95b6-4d3f-accf-90c7b183df9f"),
        verifiedAttributes = Seq.empty
      )
      (() => mockUUIDSupplier.get).expects().returning(agreementId).once()

      val response: Future[Agreement] = createAgreement(agreementSeed)

      val bodyResponse: Agreement = Await.result(response, Duration.Inf)
      bodyResponse.verifiedAttributes shouldBe empty
      bodyResponse.status shouldBe "pending"

      //when the activation occurs
      val activateAgreementResponse = activateAgreement(bodyResponse)

      //the agreement should change its status to "active"
      val activatedAgreement = Await.result(activateAgreementResponse, Duration.Inf)

      activatedAgreement.status shouldBe "active"
    }

    "should verify an attribute properly" in {
      //given an agreement with an attribute not yet verified
      val agreementId = "27f8dce0-0a5b-476b-9fdd-a7a658eb9299"
      val attributeId = "27f8dce0-0a5b-476b-9fdd-a7a658eb9284"

      val agreementSeed = AgreementSeed(
        eserviceId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9212"),
        descriptorId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9213"),
        producerId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9214"),
        consumerId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9215"),
        verifiedAttributes =
          Seq(VerifiedAttributeSeed(id = UUID.fromString(attributeId), verified = None, validityTimespan = None))
      )
      (() => mockUUIDSupplier.get).expects().returning(UUID.fromString(agreementId)).once()
      val data     = Await.result(Marshal(agreementSeed).to[MessageEntity].map(_.dataBytes), Duration.Inf)
      val response = makeRequest(data, "agreements", HttpMethods.POST)

      val bodyResponse: Agreement = Await.result(Unmarshal(response.entity).to[Agreement], Duration.Inf)
      bodyResponse.verifiedAttributes
        .find(p => p.id.toString == attributeId)
        .get
        .verified shouldBe None

      bodyResponse.verifiedAttributes
        .find(p => p.id.toString == attributeId)
        .get
        .verificationDate should be(None)

      val verifiedAttributeSeed =
        VerifiedAttributeSeed(id = UUID.fromString(attributeId), verified = Some(true), validityTimespan = None)
      val updatedSeed = Await.result(Marshal(verifiedAttributeSeed).to[MessageEntity].map(_.dataBytes), Duration.Inf)

      //when the verification occurs
      val updatedAgreementResponse = makeRequest(updatedSeed, s"agreements/$agreementId/attribute", HttpMethods.PATCH)

      //it should set its verified attribute to true and setup a verification date also.
      updatedAgreementResponse.status shouldBe StatusCodes.OK
      val updatedAgreement = Await.result(Unmarshal(updatedAgreementResponse.entity).to[Agreement], Duration.Inf)
      val updatedAttribute = updatedAgreement.verifiedAttributes.find(p => p.id.toString == attributeId).get

      updatedAttribute.verified shouldBe Some(true)
      updatedAttribute.verificationDate shouldBe a[Some[_]]
    }
  }

  "upgrade an agreement properly" in {
    //given a pending agreement
    val agreementId = UUID.randomUUID()

    val agreementSeed = AgreementSeed(
      eserviceId = UUID.fromString("94c06558-c0f9-42e0-a11e-c5f82e4b8a9b"),
      descriptorId = UUID.fromString("77262ddf-7743-4f26-85fd-ec53febbfe56"),
      producerId = UUID.fromString("f318fcc9-5421-4081-8e13-ba508152af9e"),
      consumerId = UUID.fromString("1f538520-95b6-4d3f-accf-90c7b183df9f"),
      verifiedAttributes = Seq.empty
    )
    (() => mockUUIDSupplier.get).expects().returning(agreementId).once()

    val response: Future[Agreement] = createAgreement(agreementSeed)
    val bodyResponse: Agreement     = Await.result(response, Duration.Inf)
    bodyResponse.verifiedAttributes shouldBe empty
    bodyResponse.status shouldBe "pending"

    //after its activation
    val _ = activateAgreement(bodyResponse).futureValue

    //and its upgrade
    val updateAgreementId = UUID.randomUUID()
    (() => mockUUIDSupplier.get).expects().returning(updateAgreementId).once()
    val _ = upgradeAgreement(agreementId.toString, agreementSeed).futureValue

    //when we retrieve the original agreement. it should have its state changed to "inactive"
    val inactiveAgreement = getAgreement(agreementId.toString).futureValue
    inactiveAgreement.status shouldBe "inactive"

    //when we retrieve the updated agreement, it should have its state changed to "active"
    val activeAgreement = getAgreement(updateAgreementId.toString).futureValue
    activeAgreement.status shouldBe "active"
  }

}
