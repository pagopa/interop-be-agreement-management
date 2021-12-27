package it.pagopa.pdnd.interop.uservice.agreementmanagement.server.impl

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, ShardedDaemonProcess}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.cluster.typed.{Cluster, Subscribe}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.persistence.typed.PersistenceId
import akka.projection.ProjectionBehavior
import akka.{actor => classic}
import com.atlassian.oai.validator.report.ValidationReport
import it.pagopa.pdnd.interop.commons.jwt.service.JWTReader
import it.pagopa.pdnd.interop.commons.jwt.service.impl.DefaultJWTReader
import it.pagopa.pdnd.interop.commons.jwt.{JWTConfiguration, PublicKeysHolder}
import it.pagopa.pdnd.interop.commons.utils.service.UUIDSupplier
import it.pagopa.pdnd.interop.commons.utils.service.impl.UUIDSupplierImpl
import it.pagopa.pdnd.interop.uservice.agreementmanagement.api.AgreementApi
import it.pagopa.pdnd.interop.uservice.agreementmanagement.api.impl.{
  AgreementApiMarshallerImpl,
  AgreementApiServiceImpl,
  problemOf
}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.common.system.ApplicationConfiguration
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence.{
  AgreementPersistentBehavior,
  AgreementPersistentProjection,
  Command
}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.server.Controller
import kamon.Kamon
import org.slf4j.LoggerFactory
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContextExecutor
import scala.util.Try
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

object Main extends App {

  private val logger = LoggerFactory.getLogger(this.getClass)

  val dependenciesLoaded: Try[JWTReader] = for {
    keyset <- JWTConfiguration.jwtReader.loadKeyset()
    jwtValidator = new DefaultJWTReader with PublicKeysHolder {
      var publicKeyset = keyset
    }
  } yield jwtValidator

  val jwtValidator =
    dependenciesLoaded.get //THIS IS THE END OF THE WORLD. Exceptions are welcomed here.

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

        context.log.info(
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
          AgreementApiMarshallerImpl,
          jwtValidator.OAuth2JWTValidatorAsContexts
        )

        val _ = AkkaManagement.get(classicSystem).start()

        val controller = new Controller(
          agreementApi,
          validationExceptionToRoute = Some(report => {
            val error =
              problemOf(StatusCodes.BadRequest, "0000", defaultMessage = errorFromRequestValidationReport(report))
            complete(error.status, error)(AgreementApiMarshallerImpl.toEntityMarshallerProblem)
          })
        )

        val _ = Http().newServerAt("0.0.0.0", ApplicationConfiguration.serverPort).bind(controller.routes)

        val listener = context.spawn(
          Behaviors.receive[ClusterEvent.MemberEvent]((ctx, event) => {
            ctx.log.info("MemberEvent: {}", event)
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

  private def errorFromRequestValidationReport(report: ValidationReport): String = {
    val messageStrings = report.getMessages.asScala.foldLeft[List[String]](List.empty)((tail, m) => {
      val context = m.getContext.toScala.map(c =>
        Seq(c.getRequestMethod.toScala, c.getRequestPath.toScala, c.getLocation.toScala).flatten
      )
      s"""${m.getAdditionalInfo.asScala.mkString(",")}
         |${m.getLevel} - ${m.getMessage}
         |${context.getOrElse(Seq.empty).mkString(" - ")}
         |""".stripMargin :: tail
    })

    logger.error("Request failed: {}", messageStrings.mkString)
    report.getMessages().asScala.map(_.getMessage).mkString(", ")
  }
}
