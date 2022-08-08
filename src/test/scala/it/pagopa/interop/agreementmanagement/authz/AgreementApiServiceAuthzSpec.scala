package it.pagopa.interop.agreementmanagement.authz

import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.Entity
import it.pagopa.interop.agreementmanagement.api.impl.AgreementApiMarshallerImpl._
import it.pagopa.interop.agreementmanagement.api.impl.AgreementApiServiceImpl
import it.pagopa.interop.agreementmanagement.model.persistence.Command
import it.pagopa.interop.agreementmanagement.model.{AgreementSeed, StateChangeDetails, VerifiedAttributeSeed}
import it.pagopa.interop.agreementmanagement.server.impl.Main.agreementPersistenceEntity
import it.pagopa.interop.agreementmanagement.util.{AuthorizedRoutes, ClusteredScalatestRouteTest}
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID

class AgreementApiServiceAuthzSpec extends AnyWordSpecLike with ClusteredScalatestRouteTest {

  override val testPersistentEntity: Entity[Command, ShardingEnvelope[Command]] =
    agreementPersistenceEntity

  val service: AgreementApiServiceImpl =
    AgreementApiServiceImpl(
      testTypedSystem,
      testAkkaSharding,
      testPersistentEntity,
      new UUIDSupplier           {
        override def get: UUID = UUID.randomUUID()
      },
      new OffsetDateTimeSupplier {
        override def get: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
      }
    )

  "Agreement api operation authorization spec" should {

    "accept authorized roles for addAgreement" in {
      val endpoint = AuthorizedRoutes.endpoints("addAgreement")

      val fakeSeed = AgreementSeed(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        verifiedAttributes = Seq.empty
      )

      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.addAgreement(fakeSeed) })
    }

    "accept authorized roles for getAgreement" in {
      val endpoint = AuthorizedRoutes.endpoints("getAgreement")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.getAgreement("fake") })
    }

    "accept authorized roles for activateAgreement" in {
      val endpoint = AuthorizedRoutes.endpoints("activateAgreement")

      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.activateAgreement("fake", StateChangeDetails()) }
      )

    }

    "accept authorized roles for suspendAgreement" in {
      val endpoint = AuthorizedRoutes.endpoints("suspendAgreement")

      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.suspendAgreement("fake", StateChangeDetails()) }
      )
    }

    "accept authorized roles for getAgreements" in {
      val endpoint = AuthorizedRoutes.endpoints("getAgreements")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.getAgreements(None, None, None, None, None) }
      )
    }

    "accept authorized roles for updateAgreementVerifiedAttribute" in {
      val endpoint = AuthorizedRoutes.endpoints("updateAgreementVerifiedAttribute")

      val fakeSeed = VerifiedAttributeSeed(id = UUID.randomUUID())
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.updateAgreementVerifiedAttribute("test", fakeSeed) }
      )
    }

    "accept authorized roles for upgradeAgreementById" in {
      val endpoint = AuthorizedRoutes.endpoints("upgradeAgreementById")

      val fakeSeed = AgreementSeed(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        verifiedAttributes = Seq.empty
      )

      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.upgradeAgreementById("test", fakeSeed) }
      )
    }

  }
}
