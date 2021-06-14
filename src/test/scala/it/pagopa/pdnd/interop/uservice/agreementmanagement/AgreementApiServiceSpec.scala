package it.pagopa.pdnd.interop.uservice.agreementmanagement

import akka.actor
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.{Marshal, ToEntityMarshaller}
import akka.http.scaladsl.model.{MessageEntity, StatusCodes}
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.persistence.typed.PersistenceId
import akka.stream.scaladsl.Source
import akka.util.ByteString
import it.pagopa.pdnd.interop.uservice.agreementmanagement.api.impl.{
  AgreementApiMarshallerImpl,
  AgreementApiServiceImpl
}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.api.{AgreementApi, AgreementApiMarshaller}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.api.impl._
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence.{AgreementPersistentBehavior, Command}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.{Agreement, AgreementSeed}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.server.Controller
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, TestSuite}

import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Var",
    "org.wartremover.warts.Nothing",
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.NonUnitStatements",
    "org.wartremover.warts.Any",
    "org.wartremover.warts.OptionPartial",
    "org.wartremover.warts.Null"
  )
)
class AgreementApiServiceSpec
    extends AnyWordSpec
    with TestSuite
    with Matchers
    with MockFactory
    with BeforeAndAfterAll
    with SprayJsonSupport {
  import AgreementApiServiceSpec._

  val agreementApiMarshaller: AgreementApiMarshaller = new AgreementApiMarshallerImpl
  var actorSystem: ActorSystem[Nothing]              = _
  var controller: Option[Controller]                 = None
  var bindServer: Option[Future[Http.ServerBinding]] = None

  override def beforeAll(): Unit = {
    actorSystem = ActorSystem[Nothing](
      Behaviors.setup[Nothing] { context =>
        implicit val classicSystem: actor.ActorSystem = context.system.toClassic

        val sharding: ClusterSharding = ClusterSharding(context.system)

        val agreementPersistenceEntity: Entity[Command, ShardingEnvelope[Command]] =
          Entity(typeKey = AgreementPersistentBehavior.TypeKey) { entityContext =>
            AgreementPersistentBehavior(
              entityContext.shard,
              PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId)
            )
          }

        val _ = sharding.init(agreementPersistenceEntity)

        val agreementApi = new AgreementApi(
          new AgreementApiServiceImpl(context.system, sharding, agreementPersistenceEntity, uuidSupplier),
          new AgreementApiMarshallerImpl(),
          SecurityDirectives.authenticateOAuth2("SecurityRealm", _ => Some(Seq.empty[(String, String)]))
        )

        controller = Some(new Controller(agreementApi, None))
        controller foreach { controller =>
          bindServer = Some(
            Http()
              .newServerAt("0.0.0.0", 18088)
              .bind(controller.routes)
          )

          Await.result(bindServer.get, 100.seconds)
        }

        Behaviors.empty[Nothing]
      },
      "pdnd-uservice-s3-gateway"
    )

  }

  "Working on organizations" must {

    implicit val classicSystem: actor.ActorSystem = actorSystem.toClassic
    implicit val ec: ExecutionContextExecutor     = actorSystem.executionContext

    implicit def toEntityMarshallerAgreementSeed: ToEntityMarshaller[AgreementSeed] = sprayJsonMarshaller[AgreementSeed]

    "create a new organization" in {

      (() => uuidSupplier.get).expects().returning(agreementId).once()

      val data: Source[ByteString, Any] = Await.result(Marshal(seed1).to[MessageEntity].map(_.dataBytes), Duration.Inf)

      val response = create(data, "agreements")

      val body = Await.result(Unmarshal(response.entity).to[Agreement], Duration.Inf)

      response.status mustBe StatusCodes.Created

      body mustBe expected1

    }

  }

}

object AgreementApiServiceSpec {

  lazy final val eserviceId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9210")
  lazy final val producerId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9211")
  lazy final val consumerId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9212")

  lazy final val agreementId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9213")

  lazy final val seed1 = AgreementSeed(eserviceId, producerId, consumerId)

  lazy final val expected1 = Agreement(
    id = agreementId,
    eserviceId = eserviceId,
    producerId = producerId,
    consumerId = consumerId,
    status = "active"
  )

}
