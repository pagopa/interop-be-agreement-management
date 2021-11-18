package it.pagopa.pdnd.interop.uservice.agreementmanagement.server.impl

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, ShardedDaemonProcess}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.cluster.typed.{Cluster, Subscribe}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.persistence.typed.PersistenceId
import akka.projection.ProjectionBehavior
import akka.{actor => classic}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.api.AgreementApi
import it.pagopa.pdnd.interop.uservice.agreementmanagement.api.impl.{AgreementApiMarshallerImpl, AgreementApiServiceImpl}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.common.system.{ApplicationConfiguration, Authenticator}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence.{AgreementPersistentBehavior, AgreementPersistentProjection, Command}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.server.Controller
import it.pagopa.pdnd.interop.uservice.agreementmanagement.service.UUIDSupplier
import it.pagopa.pdnd.interop.uservice.agreementmanagement.service.impl.UUIDSupplierImpl
import kamon.Kamon
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.concurrent.ExecutionContextExecutor

object Main extends App {

  Kamon.init()

  def buildPersistentEntity(): Entity[Command, ShardingEnvelope[Command]] =
    Entity(typeKey = AgreementPersistentBehavior.TypeKey) { entityContext =>
      AgreementPersistentBehavior(
        entityContext.shard,
        PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId)
      )
    }

  locally {
    val _ = ActorSystem[Nothing](
      Behaviors.setup[Nothing] { context =>
        import akka.actor.typed.scaladsl.adapter._
        implicit val classicSystem: classic.ActorSystem         = context.system.toClassic
        implicit val executionContext: ExecutionContextExecutor = context.system.executionContext

        val cluster = Cluster(context.system)

        context.log.error(
          "Started [" + context.system + "], cluster.selfAddress = " + cluster.selfMember.address + ", build info = " + buildinfo.BuildInfo.toString + ")"
        )

        val sharding: ClusterSharding = ClusterSharding(context.system)

        val agreementPersistenceEntity: Entity[Command, ShardingEnvelope[Command]] = buildPersistentEntity()

        val _ = sharding.init(agreementPersistenceEntity)

        val settings: ClusterShardingSettings = agreementPersistenceEntity.settings match {
          case None    => ClusterShardingSettings(context.system)
          case Some(s) => s
        }
        val persistence =
          classicSystem.classicSystem.settings.config.getString("uservice-agreement-management.persistence")
        if (persistence == "jdbc-journal") {
          val dbConfig: DatabaseConfig[JdbcProfile] =
            DatabaseConfig.forConfig("akka-persistence-jdbc.shared-databases.slick")

          val agreementPersistentProjection =
            new AgreementPersistentProjection(context.system, agreementPersistenceEntity, dbConfig)

          ShardedDaemonProcess(context.system).init[ProjectionBehavior.Command](
            name = "agreement-projections",
            numberOfInstances = settings.numberOfShards,
            behaviorFactory = (i: Int) => ProjectionBehavior(agreementPersistentProjection.projections(i)),
            stopMessage = ProjectionBehavior.Stop
          )
        }

        val uuidSupplier: UUIDSupplier = new UUIDSupplierImpl

        val agreementApi = new AgreementApi(
          new AgreementApiServiceImpl(context.system, sharding, agreementPersistenceEntity, uuidSupplier),
          new AgreementApiMarshallerImpl(),
          SecurityDirectives.authenticateOAuth2("SecurityRealm", Authenticator)
        )

        val _ = AkkaManagement.get(classicSystem).start()

        val controller = new Controller(
          agreementApi,
          validationExceptionToRoute = Some(e => {
            val results = e.results()
            results.crumbs().asScala.foreach { crumb =>
              println(crumb.crumb())
            }
            results.items().asScala.foreach { item =>
              println(item.dataCrumbs())
              println(item.dataJsonPointer())
              println(item.schemaCrumbs())
              println(item.message())
              println(item.severity())
            }
            val message = e.results().items().asScala.map(_.message()).mkString("\n")
            complete((400, message))
          })
        )

        val _ = Http().newServerAt("0.0.0.0", ApplicationConfiguration.serverPort).bind(controller.routes)

        val listener = context.spawn(
          Behaviors.receive[ClusterEvent.MemberEvent]((ctx, event) => {
            ctx.log.error("MemberEvent: {}", event)
            Behaviors.same
          }),
          "listener"
        )

        Cluster(context.system).subscriptions ! Subscribe(listener, classOf[ClusterEvent.MemberEvent])

        val _ = AkkaManagement(classicSystem).start()
        ClusterBootstrap.get(classicSystem).start()
        Behaviors.empty
      },
      "pdnd-interop-uservice-agreement-management"
    )
  }
}
