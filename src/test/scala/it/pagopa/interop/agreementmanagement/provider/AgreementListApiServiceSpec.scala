package it.pagopa.interop.agreementmanagement.provider

import akka.actor
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Join}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import akka.http.scaladsl.unmarshalling.Unmarshal
import it.pagopa.interop.agreementmanagement.api.AgreementApi
import it.pagopa.interop.agreementmanagement.api.impl.{AgreementApiMarshallerImpl, AgreementApiServiceImpl}
import it.pagopa.interop.agreementmanagement.model.Agreement
import it.pagopa.interop.agreementmanagement.model.persistence.AgreementPersistentBehavior
import it.pagopa.interop.agreementmanagement.server.Controller
import it.pagopa.interop.agreementmanagement.server.impl.Dependencies
import it.pagopa.interop.agreementmanagement.{
  AdminMockAuthenticator,
  SpecConfiguration,
  SpecHelper,
  emptyData,
  makeRequest,
  mockDateTimeSupplier,
  mockUUIDSupplier
}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

/** Local integration test.
  *
  * Starts a local cluster sharding and invokes REST operations on the eventsourcing entity
  */
class AgreementListApiServiceSpec
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

      prepareDataForListingTests
    }
  }

  override def afterAll(): Unit = {
    // println("****** Cleaning resources ********")
    bindServer.foreach(_.foreach(_.unbind()))
    ActorTestKit.shutdown(httpSystem, 5.seconds)
    super.afterAll()
    // println("Resources cleaned")
  }

  "Listing agreements " must {

    "retrieves all agreements for a given consumerId" in {

      val response = makeRequest(emptyData, s"agreements?consumerId=${AgreementOne.consumerId}", HttpMethods.GET)

      val agreements: Seq[Agreement] =
        Await.result(Unmarshal(response.entity).to[Seq[Agreement]], Duration.Inf)

      agreements.size should be(1)
      agreements.head.consumerId should be(AgreementOne.consumerId)
    }
    "retrieves all agreements for a given produceId" in {

      val response = makeRequest(emptyData, s"agreements?producerId=${AgreementTwo.producerId}", HttpMethods.GET)

      val agreements: Seq[Agreement] =
        Await.result(Unmarshal(response.entity).to[Seq[Agreement]], Duration.Inf)

      agreements.size should be(1)
      agreements.head.producerId should be(AgreementTwo.producerId)
    }
    "retrieves all agreements for a given eserviceId" in {

      val response = makeRequest(emptyData, s"agreements?eserviceId=${AgreementThree.eserviceId}", HttpMethods.GET)

      val agreements: Seq[Agreement] =
        Await.result(Unmarshal(response.entity).to[Seq[Agreement]], Duration.Inf)

      agreements.size should be(1)
      agreements.head.eserviceId should be(AgreementThree.eserviceId)
    }
    "retrieves all draft agreements" in {

      val response = makeRequest(emptyData, s"agreements?states=DRAFT", HttpMethods.GET)

      val agreements: Seq[Agreement] =
        Await.result(Unmarshal(response.entity).to[Seq[Agreement]], Duration.Inf)

      agreements.size should be(1)
      agreements.head.id should be(AgreementOne.agreementId)
    }
    "retrieves all pending agreements" in {

      val response = makeRequest(emptyData, s"agreements?states=PENDING", HttpMethods.GET)

      val agreements: Seq[Agreement] =
        Await.result(Unmarshal(response.entity).to[Seq[Agreement]], Duration.Inf)

      agreements.size should be(1)
      agreements.head.id should be(AgreementFour.agreementId)
    }
    "retrieves all activated agreements" in {

      val response = makeRequest(emptyData, s"agreements?states=ACTIVE", HttpMethods.GET)

      val agreements: Seq[Agreement] =
        Await.result(Unmarshal(response.entity).to[Seq[Agreement]], Duration.Inf)

      agreements.size should be(1)
      agreements.head.id should be(AgreementTwo.agreementId)
    }
    "retrieves all suspended agreements" in {

      val response = makeRequest(emptyData, s"agreements?states=SUSPENDED", HttpMethods.GET)

      val agreements: Seq[Agreement] =
        Await.result(Unmarshal(response.entity).to[Seq[Agreement]], Duration.Inf)

      agreements.size should be(1)
      agreements.head.id should be(AgreementThree.agreementId)
    }

    "retrieves draft and active agreements" in {

      val response = makeRequest(emptyData, s"agreements?states=DRAFT,ACTIVE", HttpMethods.GET)

      val agreements: Seq[Agreement] =
        Await.result(Unmarshal(response.entity).to[Seq[Agreement]], Duration.Inf)

      agreements.map(_.id) should contain only (AgreementOne.agreementId, AgreementTwo.agreementId)
    }

    "retrieves all agreements containing the given attribute" in {

      val response = makeRequest(emptyData, s"agreements?attributeId=$attributeId", HttpMethods.GET)

      val agreements: Seq[Agreement] =
        Await.result(Unmarshal(response.entity).to[Seq[Agreement]], Duration.Inf)

      agreements.size should be(3)
      agreements.map(
        _.id
      ) should contain only (AgreementTwo.agreementId, AgreementThree.agreementId, AgreementFour.agreementId)
    }
  }
}
