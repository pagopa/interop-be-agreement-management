package it.pagopa.interop.agreementmanagement

import akka.actor
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.typed.{Cluster, Join}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import it.pagopa.interop.agreementmanagement.api._
import it.pagopa.interop.agreementmanagement.api.impl._
import it.pagopa.interop.agreementmanagement.common.system.ApplicationConfiguration
import it.pagopa.interop.agreementmanagement.model.agreement._
import it.pagopa.interop.agreementmanagement.model.persistence._
import it.pagopa.interop.agreementmanagement.server.Controller
import it.pagopa.interop.agreementmanagement.server.impl.Dependencies
import it.pagopa.interop.commons.utils.AkkaUtils.getShard
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import org.scalamock.scalatest.MockFactory
import org.scalatest.Assertion
import spray.json._

import java.net.InetAddress
import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

trait ItSpecHelper
    extends ItSpecConfiguration
    with ItCqrsSpec
    with MockFactory
    with SprayJsonSupport
    with DefaultJsonProtocol
    with Dependencies {
  self: ScalaTestWithActorTestKit =>

  val bearerToken: String                   = "token"
  final val requestHeaders: Seq[HttpHeader] =
    Seq(
      headers.Authorization(OAuth2BearerToken("token")),
      headers.RawHeader("X-Correlation-Id", "test-id"),
      headers.`X-Forwarded-For`(RemoteAddress(InetAddress.getByName("127.0.0.1")))
    )

  val mockUUIDSupplier: UUIDSupplier               = mock[UUIDSupplier]
  val mockDateTimeSupplier: OffsetDateTimeSupplier = mock[OffsetDateTimeSupplier]

  val apiMarshaller: AgreementApiMarshaller = AgreementApiMarshallerImpl

  var controller: Option[Controller]                 = None
  var bindServer: Option[Future[Http.ServerBinding]] = None

  val wrappingDirective: AuthenticationDirective[Seq[(String, String)]] =
    SecurityDirectives.authenticateOAuth2("SecurityRealm", AdminMockAuthenticator)

  val sharding: ClusterSharding                 = ClusterSharding(system)
  def commander(id: UUID): EntityRef[Command]   = commander(id.toString)
  def commander(id: String): EntityRef[Command] =
    sharding.entityRefFor(
      AgreementPersistentBehavior.TypeKey,
      getShard(id, ApplicationConfiguration.numberOfProjectionTags)
    )

  val httpSystem: ActorSystem[Any]                        =
    ActorSystem(Behaviors.ignore[Any], name = system.name, config = system.settings.config)
  implicit val executionContext: ExecutionContextExecutor = httpSystem.executionContext
  val classicSystem: actor.ActorSystem                    = httpSystem.classicSystem

  override def startServer(): Unit = {
    val persistentEntity: Entity[Command, ShardingEnvelope[Command]] =
      Entity(AgreementPersistentBehavior.TypeKey)(behaviorFactory())

    Cluster(system).manager ! Join(Cluster(system).selfMember.address)
    sharding.init(persistentEntity)

    val attributeApi =
      new AgreementApi(
        AgreementApiServiceImpl(system, sharding, persistentEntity, mockUUIDSupplier, mockDateTimeSupplier),
        apiMarshaller,
        wrappingDirective
      )

    if (ApplicationConfiguration.projectionsEnabled) initCqrsProjection

    controller = Some(new Controller(attributeApi)(classicSystem))

    controller foreach { controller =>
      bindServer = Some(
        Http()(classicSystem)
          .newServerAt("0.0.0.0", 18088)
          .bind(controller.routes)
      )

      Await.result(bindServer.get, 100.seconds)
    }
  }

  override def shutdownServer(): Unit = {
    bindServer.foreach(_.foreach(_.unbind()))
    ActorTestKit.shutdown(httpSystem, 5.seconds)
  }

  def compareAgreements(item1: PersistentAgreement, item2: PersistentAgreement): Assertion =
    sortArrayFields(item1) shouldBe sortArrayFields(item2)

  def sortArrayFields(agreement: PersistentAgreement): PersistentAgreement =
    agreement.copy(verifiedAttributes = agreement.verifiedAttributes.sortBy(_.id))

  def createAgreement(agreement: PersistentAgreement): PersistentAgreement =
    commander(agreement.id).ask(ref => AddAgreement(agreement, ref)).futureValue.getValue

  def updateAgreement(agreement: PersistentAgreement): PersistentAgreement =
    commander(agreement.id).ask(ref => UpdateAgreement(agreement, ref)).futureValue.getValue

  def deletedAgreement(agreementId: String): Unit =
    commander(agreementId).ask(ref => DeleteAgreement(agreementId, ref)).futureValue.getValue

  def addContract(agreementId: UUID, contract: PersistentAgreementDocument): PersistentAgreementDocument =
    commander(agreementId)
      .ask(ref => AddAgreementContract(agreementId.toString, contract, ref))
      .futureValue
      .getValue

  def addConsumerDocument(agreementId: UUID, document: PersistentAgreementDocument): PersistentAgreementDocument =
    commander(agreementId)
      .ask(ref => AddAgreementConsumerDocument(agreementId.toString, document, ref))
      .futureValue
      .getValue

  def removeConsumerDocument(agreementId: UUID, documentId: UUID): Unit =
    commander(agreementId)
      .ask(ref => RemoveAgreementConsumerDocument(agreementId.toString, documentId.toString, ref))
      .futureValue
      .getValue

}
