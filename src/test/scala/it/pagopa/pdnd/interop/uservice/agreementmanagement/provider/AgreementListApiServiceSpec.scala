package it.pagopa.pdnd.interop.uservice.agreementmanagement.provider

import akka.actor
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.typed.{Cluster, Join}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import akka.http.scaladsl.unmarshalling.Unmarshal
import it.pagopa.pdnd.interop.commons.utils.AkkaUtils.Authenticator
import it.pagopa.pdnd.interop.uservice.agreementmanagement.api.AgreementApi
import it.pagopa.pdnd.interop.uservice.agreementmanagement.api.impl.{
  AgreementApiMarshallerImpl,
  AgreementApiServiceImpl
}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.Agreement
import it.pagopa.pdnd.interop.uservice.agreementmanagement.server.Controller
import it.pagopa.pdnd.interop.uservice.agreementmanagement.server.impl.Main
import it.pagopa.pdnd.interop.uservice.agreementmanagement.{
  SpecConfiguration,
  SpecHelper,
  emptyData,
  makeRequest,
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
    with AnyWordSpecLike
    with SpecConfiguration
    with SpecHelper {

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
    println("****** Cleaning resources ********")
    bindServer.foreach(_.foreach(_.unbind()))
    ActorTestKit.shutdown(httpSystem, 5.seconds)
    super.afterAll()
    println("Resources cleaned")
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
    "retrieves all pending agreements" in {

      val response = makeRequest(emptyData, s"agreements?state=PENDING", HttpMethods.GET)

      val agreements: Seq[Agreement] =
        Await.result(Unmarshal(response.entity).to[Seq[Agreement]], Duration.Inf)

      agreements.size should be(1)
      agreements.head.id should be(AgreementOne.agreementId)
    }
    "retrieves all activated agreements" in {

      val response = makeRequest(emptyData, s"agreements?state=ACTIVE", HttpMethods.GET)

      val agreements: Seq[Agreement] =
        Await.result(Unmarshal(response.entity).to[Seq[Agreement]], Duration.Inf)

      agreements.size should be(1)
      agreements.head.id should be(AgreementTwo.agreementId)
    }
    "retrieves all suspended agreements" in {

      val response = makeRequest(emptyData, s"agreements?state=SUSPENDED", HttpMethods.GET)

      val agreements: Seq[Agreement] =
        Await.result(Unmarshal(response.entity).to[Seq[Agreement]], Duration.Inf)

      agreements.size should be(1)
      agreements.head.id should be(AgreementThree.agreementId)
    }
  }
}
