package it.pagopa.interop.agreementmanagement.server.impl

import it.pagopa.interop.agreementmanagement.model.persistence.AgreementPersistentBehavior
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{JWTConfiguration, KID, PublicKeysHolder, SerializedKey}
import scala.concurrent.{Future, ExecutionContext}
import akka.persistence.typed.PersistenceId
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.actor.typed.Behavior
import it.pagopa.interop.agreementmanagement.model.persistence.Command
import akka.cluster.sharding.typed.scaladsl.{Entity, EntityContext}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.agreementmanagement.common.system.ApplicationConfiguration
import it.pagopa.interop.commons.utils.service.UUIDSupplier
import it.pagopa.interop.commons.utils.service.impl.UUIDSupplierImpl
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier
import it.pagopa.interop.commons.utils.service.impl.OffsetDateTimeSupplierImpl
import it.pagopa.interop.agreementmanagement.common.system.ApplicationConfiguration.{
  numberOfProjectionTags,
  projectionTag
}
import it.pagopa.interop.commons.queue.QueueWriter
import it.pagopa.interop.agreementmanagement.model.persistence.AgreementEventsSerde
import it.pagopa.interop.agreementmanagement.model.persistence.AgreementPersistentProjection
import slick.basic.DatabaseConfig
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.projection.ProjectionBehavior
import it.pagopa.interop.agreementmanagement.api.AgreementApi
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import it.pagopa.interop.agreementmanagement.api.impl._
import com.atlassian.oai.validator.report.ValidationReport
import akka.http.scaladsl.server.Route
import it.pagopa.interop.commons.utils.OpenapiUtils
import akka.http.scaladsl.model.StatusCodes
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import akka.http.scaladsl.server.Directives.complete

trait Dependencies {

  val uuidSupplier: UUIDSupplier               = new UUIDSupplierImpl
  val dateTimeSupplier: OffsetDateTimeSupplier = OffsetDateTimeSupplierImpl

  val behaviorFactory: EntityContext[Command] => Behavior[Command] = { entityContext =>
    val index: Int = math.abs(entityContext.entityId.hashCode % numberOfProjectionTags)
    AgreementPersistentBehavior(
      entityContext.shard,
      PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
      OffsetDateTimeSupplierImpl,
      projectionTag(index)
    )
  }

  val agreementPersistenceEntity: Entity[Command, ShardingEnvelope[Command]] =
    Entity(AgreementPersistentBehavior.TypeKey)(behaviorFactory)

  def initProjections()(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): Unit = {
    val queueWriter: QueueWriter =
      QueueWriter.get(ApplicationConfiguration.queueUrl)(AgreementEventsSerde.agreementToJson)

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

  def getJwtValidator()(implicit ec: ExecutionContext): Future[JWTReader] = JWTConfiguration.jwtReader
    .loadKeyset()
    .toFuture
    .map(keyset =>
      new DefaultJWTReader with PublicKeysHolder {
        var publicKeyset: Map[KID, SerializedKey]                                        = keyset
        override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
          getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
      }
    )

  def agreementApi(sharding: ClusterSharding, jwtReader: JWTReader)(implicit
    actorSystem: ActorSystem[_],
    ec: ExecutionContext
  ) = new AgreementApi(
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
