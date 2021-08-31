package it.pagopa.pdnd.interop.uservice.agreementmanagement

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.typed.{Cluster, Join}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, MessageEntity, StatusCodes}
import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.ConfigFactory
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
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

object AgreementApiServiceSpec {

  val testData = ConfigFactory.parseString(s"""
      akka.actor.provider = cluster

      akka.remote.classic.netty.tcp.port = 0
      akka.remote.artery.canonical.port = 0
      akka.remote.artery.canonical.hostname = 127.0.0.1

      akka.cluster.jmx.multi-mbeans-in-same-jvm = on

      akka.cluster.sharding.number-of-shards = 10

      akka.coordinated-shutdown.terminate-actor-system = off
      akka.coordinated-shutdown.run-by-actor-system-terminate = off
      akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
      akka.cluster.run-coordinated-shutdown-when-down = off
    """)

  val config = ConfigFactory
    .parseResourcesAnySyntax("test")
    .withFallback(testData)

  val mockUUIDSupplier: UUIDSupplier = mock[UUIDSupplier]
}

/** Local integration test.
  *
  * Starts a local cluster sharding and invokes REST operations on the eventsourcing entity
  */
class AgreementApiServiceSpec extends ScalaTestWithActorTestKit(AgreementApiServiceSpec.config) with AnyWordSpecLike {

  val payloadMarshaller: AgreementApiMarshaller      = new AgreementApiMarshallerImpl
  var controller: Option[Controller]                 = None
  var bindServer: Option[Future[Http.ServerBinding]] = None
  val wrappingDirective: AuthenticationDirective[Seq[(String, String)]] =
    SecurityDirectives.authenticateOAuth2("SecurityRealm", Authenticator)

  val sharding = ClusterSharding(system)

  val httpSystem                                          = ActorSystem(Behaviors.ignore[Any], name = system.name, config = system.settings.config)
  implicit val executionContext: ExecutionContextExecutor = httpSystem.executionContext
  implicit val classicSystem                              = httpSystem.classicSystem

  import AgreementApiServiceSpec._

  override def beforeAll(): Unit = {

    val persistentEntity = Main.buildPersistentEntity()

    Cluster(system).manager ! Join(Cluster(system).selfMember.address)
    sharding.init(persistentEntity)

    val partyApi = new AgreementApi(
      new AgreementApiServiceImpl(system, sharding, persistentEntity, mockUUIDSupplier),
      payloadMarshaller,
      wrappingDirective
    )

    controller = Some(new Controller(partyApi)(classicSystem))

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

  "Processing a request payload" should {

    "create a new agreement" in {
      val uuid = "27f8dce0-0a5b-476b-9fdd-a7a658eb9211"
      val agreementSeed = AgreementSeed(
        eserviceId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9212"),
        producerId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9213"),
        consumerId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9214"),
        verifiedAttributes = Seq(
          VerifiedAttributeSeed(
            id = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9215"),
            verified = true,
            validityTimespan = None
          ),
          VerifiedAttributeSeed(
            id = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9216"),
            verified = false,
            validityTimespan = None
          ),
          VerifiedAttributeSeed(
            id = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9217"),
            verified = false,
            validityTimespan = Some(123L)
          )
        )
      )
      (() => mockUUIDSupplier.get).expects().returning(UUID.fromString(uuid)).once()

      val data     = Await.result(Marshal(agreementSeed).to[MessageEntity].map(_.dataBytes), Duration.Inf)
      val response = makeRequest(data, "agreements", HttpMethods.POST)

      val bodyResponse: Agreement = Await.result(Unmarshal(response.entity).to[Agreement], Duration.Inf)

      bodyResponse.id.toString shouldBe uuid
      bodyResponse.status shouldBe "pending"
      bodyResponse.verifiedAttributes
        .find(p => p.id.toString == "27f8dce0-0a5b-476b-9fdd-a7a658eb9215")
        .get
        .verificationDate shouldBe a[Some[_]]

      bodyResponse.verifiedAttributes
        .find(p => p.id.toString == "27f8dce0-0a5b-476b-9fdd-a7a658eb9216")
        .get
        .verificationDate should be(None)

      bodyResponse.verifiedAttributes
        .find(p => p.id.toString == "27f8dce0-0a5b-476b-9fdd-a7a658eb9217")
        .get
        .validityTimespan
        .get should be(123L)
    }
  }

  "should activate an agreement properly" in {
    //given a pending agreement
    val uuid = "27f8dce0-0a5b-476b-9fdd-a7a658eb9224"

    val agreementSeed = AgreementSeed(
      eserviceId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9212"),
      producerId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9213"),
      consumerId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9214"),
      verifiedAttributes = Seq.empty
    )
    (() => mockUUIDSupplier.get).expects().returning(UUID.fromString(uuid)).once()
    val data     = Await.result(Marshal(agreementSeed).to[MessageEntity].map(_.dataBytes), Duration.Inf)
    val response = makeRequest(data, "agreements", HttpMethods.POST)

    val bodyResponse: Agreement = Await.result(Unmarshal(response.entity).to[Agreement], Duration.Inf)
    bodyResponse.verifiedAttributes shouldBe empty
    bodyResponse.status shouldBe "pending"

    //when the activation occurs
    val activateAgreementResponse = Await.result(
      Http().singleRequest(
        HttpRequest(uri = s"$url/agreements/$uuid/activate", method = HttpMethods.PATCH, headers = authorization)
      ),
      Duration.Inf
    )

    //the agreement should change its status to "active"
    activateAgreementResponse.status shouldBe StatusCodes.OK
    val activatedAgreement = Await.result(Unmarshal(activateAgreementResponse.entity).to[Agreement], Duration.Inf)

    activatedAgreement.status shouldBe "active"
  }

  "should verify an attribute properly" in {
    //given an agreement with an attribute not yet verified
    val uuid        = "27f8dce0-0a5b-476b-9fdd-a7a658eb9299"
    val attributeId = "27f8dce0-0a5b-476b-9fdd-a7a658eb9284"

    val agreementSeed = AgreementSeed(
      eserviceId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9212"),
      producerId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9213"),
      consumerId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9214"),
      verifiedAttributes =
        Seq(VerifiedAttributeSeed(id = UUID.fromString(attributeId), verified = false, validityTimespan = None))
    )
    (() => mockUUIDSupplier.get).expects().returning(UUID.fromString(uuid)).once()
    val data     = Await.result(Marshal(agreementSeed).to[MessageEntity].map(_.dataBytes), Duration.Inf)
    val response = makeRequest(data, "agreements", HttpMethods.POST)

    val bodyResponse: Agreement = Await.result(Unmarshal(response.entity).to[Agreement], Duration.Inf)
    bodyResponse.verifiedAttributes
      .find(p => p.id.toString == attributeId)
      .get
      .verified should be(false)

    bodyResponse.verifiedAttributes
      .find(p => p.id.toString == attributeId)
      .get
      .verificationDate should be(None)

    val verifiedAttributeSeed =
      VerifiedAttributeSeed(id = UUID.fromString(attributeId), verified = true, validityTimespan = None)
    val updatedSeed = Await.result(Marshal(verifiedAttributeSeed).to[MessageEntity].map(_.dataBytes), Duration.Inf)

    //when the verification occurs
    val updatedAgreementResponse = makeRequest(updatedSeed, s"agreements/${uuid}/attribute", HttpMethods.PATCH)

    //it should set its verified attribute to true and setup a verification date also.
    updatedAgreementResponse.status shouldBe StatusCodes.OK
    val updatedAgreement = Await.result(Unmarshal(updatedAgreementResponse.entity).to[Agreement], Duration.Inf)
    val updatedAttribute = updatedAgreement.verifiedAttributes.find(p => p.id.toString == attributeId).get

    updatedAttribute.verified should be(true)
    updatedAttribute.verificationDate shouldBe a[Some[_]]
  }

}
