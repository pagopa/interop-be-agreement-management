package it.pagopa.interop.agreementmanagement.server.impl

import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, ShardedDaemonProcess}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import akka.persistence.typed.PersistenceId
import akka.projection.ProjectionBehavior
import com.atlassian.oai.validator.report.ValidationReport
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import it.pagopa.interop.agreementmanagement.api.AgreementApi
import it.pagopa.interop.agreementmanagement.api.impl._
import it.pagopa.interop.agreementmanagement.common.system.ApplicationConfiguration
import it.pagopa.interop.agreementmanagement.common.system.ApplicationConfiguration.{
  numberOfProjectionTags,
  projectionTag
}
import it.pagopa.interop.agreementmanagement.model.persistence.{
  AgreementEventsSerde,
  AgreementPersistentBehavior,
  AgreementPersistentProjection,
  Command
}
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{JWTConfiguration, KID, PublicKeysHolder, SerializedKey}
import it.pagopa.interop.commons.queue.QueueWriter
import it.pagopa.interop.commons.utils.OpenapiUtils
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.interop.commons.utils.service.impl.{OffsetDateTimeSupplierImpl, UUIDSupplierImpl}
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import slick.basic.DatabaseConfig

import scala.concurrent.{ExecutionContext, Future}

trait Dependencies {

  val uuidSupplier: UUIDSupplier               = new UUIDSupplierImpl
  val dateTimeSupplier: OffsetDateTimeSupplier = OffsetDateTimeSupplierImpl

  val behaviorFactory: EntityContext[Command] => Behavior[Command] = { entityContext =>
    val index: Int = math.abs(entityContext.entityId.hashCode % numberOfProjectionTags)
    AgreementPersistentBehavior(
      entityContext.shard,
      PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
      projectionTag(index)
    )
  }

  val agreementPersistenceEntity: Entity[Command, ShardingEnvelope[Command]] =
    Entity(AgreementPersistentBehavior.TypeKey)(behaviorFactory)

  def initProjections(blockingEc: ExecutionContext)(implicit actorSystem: ActorSystem[_]): Unit = {
    val queueWriter: QueueWriter =
      QueueWriter.get(ApplicationConfiguration.queueUrl)(AgreementEventsSerde.agreementToJson)(blockingEc)

    val agreementPersistentProjection: AgreementPersistentProjection = new AgreementPersistentProjection(
      DatabaseConfig.forConfig("akka-persistence-jdbc.shared-databases.slick"),
      queueWriter
    )

    ShardedDaemonProcess(actorSystem).init[ProjectionBehavior.Command](
      name = "agreement-projections",
      numberOfInstances = numberOfProjectionTags,
      behaviorFactory = (i: Int) => ProjectionBehavior(agreementPersistentProjection.projection(projectionTag(i))),
      stopMessage = ProjectionBehavior.Stop
    )
  }

  def getJwtValidator(): Future[JWTReader] = JWTConfiguration.jwtReader
    .loadKeyset()
    .map(keyset =>
      new DefaultJWTReader with PublicKeysHolder {
        var publicKeyset: Map[KID, SerializedKey]                                        = keyset
        override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
          getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
      }
    )
    .toFuture

  def agreementApi(sharding: ClusterSharding, jwtReader: JWTReader)(implicit
    actorSystem: ActorSystem[_],
    ec: ExecutionContext
  ): AgreementApi = new AgreementApi(
    AgreementApiServiceImpl(actorSystem, sharding, agreementPersistenceEntity, uuidSupplier, dateTimeSupplier),
    AgreementApiMarshallerImpl,
    jwtReader.OAuth2JWTValidatorAsContexts
  )

  val validationExceptionToRoute: ValidationReport => Route = report => {
    val validationReport: String = OpenapiUtils.errorFromRequestValidationReport(report)
    val error = problemOf(StatusCodes.BadRequest, GenericComponentErrors.ValidationRequestError(validationReport))
    complete(error.status, error)(AgreementApiMarshallerImpl.toEntityMarshallerProblem)
  }
}
