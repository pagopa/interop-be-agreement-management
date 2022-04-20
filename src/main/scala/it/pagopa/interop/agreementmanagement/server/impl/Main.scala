package it.pagopa.interop.agreementmanagement.server.impl

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.ClusterEvent
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, ShardedDaemonProcess}
import akka.cluster.typed.{Cluster, Subscribe}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.persistence.typed.PersistenceId
import akka.projection.ProjectionBehavior
import akka.{actor => classic}
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import it.pagopa.interop.agreementmanagement.api.AgreementApi
import it.pagopa.interop.agreementmanagement.api.impl.{AgreementApiMarshallerImpl, AgreementApiServiceImpl, problemOf}
import it.pagopa.interop.agreementmanagement.common.system.ApplicationConfiguration
import it.pagopa.interop.agreementmanagement.common.system.ApplicationConfiguration.{
  numberOfProjectionTags,
  projectionTag,
  projectionsEnabled
}
import it.pagopa.interop.agreementmanagement.model.persistence.{
  AgreementPersistentBehavior,
  AgreementPersistentProjection,
  Command
}
import it.pagopa.interop.agreementmanagement.server.Controller
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{JWTConfiguration, KID, PublicKeysHolder, SerializedKey}
import it.pagopa.interop.commons.utils.OpenapiUtils
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.ValidationRequestError
import it.pagopa.interop.commons.utils.service.impl.{OffsetDateTimeSupplierImpl, UUIDSupplierImpl}
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import kamon.Kamon
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContextExecutor
import scala.util.Try
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors
import it.pagopa.interop.commons.queue.QueueWriter
import it.pagopa.interop.agreementmanagement.model.persistence.AgreementEventsSerde

object Main extends App {

  val dependenciesLoaded: Try[JWTReader] = for {
    keyset <- JWTConfiguration.jwtReader.loadKeyset()
    jwtValidator = new DefaultJWTReader with PublicKeysHolder {
      var publicKeyset: Map[KID, SerializedKey]                                        = keyset
      override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
        getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
    }
  } yield jwtValidator

  val jwtValidator = dependenciesLoaded.get // THIS IS THE END OF THE WORLD. Exceptions are welcomed here.

  val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4))
  val queueWriter: QueueWriter     =
    QueueWriter.get(ApplicationConfiguration.queueUrl)(AgreementEventsSerde.purposeToJson)(ec)

  Kamon.init()

  lazy val behaviorFactory: EntityContext[Command] => Behavior[Command] = { entityContext =>
    val index = math.abs(entityContext.entityId.hashCode % numberOfProjectionTags)
    AgreementPersistentBehavior(
      entityContext.shard,
      PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
      OffsetDateTimeSupplierImpl,
      projectionTag(index)
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

        val uuidSupplier: UUIDSupplier               = new UUIDSupplierImpl
        val dateTimeSupplier: OffsetDateTimeSupplier = OffsetDateTimeSupplierImpl

        val agreementPersistenceEntity: Entity[Command, ShardingEnvelope[Command]] =
          Entity(AgreementPersistentBehavior.TypeKey)(behaviorFactory)

        val _ = sharding.init(agreementPersistenceEntity)

        if (projectionsEnabled) {
          val dbConfig: DatabaseConfig[JdbcProfile] =
            DatabaseConfig.forConfig("akka-persistence-jdbc.shared-databases.slick")

          val agreementPersistentProjection =
            new AgreementPersistentProjection(dbConfig, queueWriter, ec)(context.system)

          ShardedDaemonProcess(context.system).init[ProjectionBehavior.Command](
            name = "agreement-projections",
            numberOfInstances = numberOfProjectionTags,
            behaviorFactory =
              (i: Int) => ProjectionBehavior(agreementPersistentProjection.projection(projectionTag(i))),
            stopMessage = ProjectionBehavior.Stop
          )
        }

        val agreementApi = new AgreementApi(
          AgreementApiServiceImpl(context.system, sharding, agreementPersistenceEntity, uuidSupplier, dateTimeSupplier),
          AgreementApiMarshallerImpl,
          jwtValidator.OAuth2JWTValidatorAsContexts
        )

        val _ = AkkaManagement.get(classicSystem).start()

        val controller = new Controller(
          agreementApi,
          validationExceptionToRoute = Some(report => {
            val error =
              problemOf(
                StatusCodes.BadRequest,
                ValidationRequestError(OpenapiUtils.errorFromRequestValidationReport(report))
              )
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
      "interop-be-agreement-management"
    )
  }

}
