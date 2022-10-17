package it.pagopa.interop.agreementmanagement.authz

import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.Entity
import it.pagopa.interop.agreementmanagement.api.impl.AgreementApiMarshallerImpl._
import it.pagopa.interop.agreementmanagement.api.impl.AgreementApiServiceImpl
import it.pagopa.interop.agreementmanagement.model.AgreementState.DRAFT
import it.pagopa.interop.agreementmanagement.model.persistence.Command
import it.pagopa.interop.agreementmanagement.model.{
  AgreementSeed,
  DocumentSeed,
  Stamp,
  Stamps,
  UpdateAgreementSeed,
  UpgradeAgreementSeed
}
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
        verifiedAttributes = Seq.empty,
        certifiedAttributes = Seq.empty,
        declaredAttributes = Seq.empty
      )

      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.addAgreement(fakeSeed) })
    }

    "accept authorized roles for updateAgreementById" in {
      val endpoint = AuthorizedRoutes.endpoints("updateAgreementById")

      val fakeSeed = UpdateAgreementSeed(
        state = DRAFT,
        verifiedAttributes = Seq.empty,
        certifiedAttributes = Seq.empty,
        declaredAttributes = Seq.empty,
        stamps = Stamps()
      )

      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.updateAgreementById("agreementId", fakeSeed) }
      )
    }
    "accept authorized roles for getAgreement" in {
      val endpoint = AuthorizedRoutes.endpoints("getAgreement")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.getAgreement("fake") })
    }

    "accept authorized roles for getAgreements" in {
      val endpoint = AuthorizedRoutes.endpoints("getAgreements")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.getAgreements(None, None, None, None, "", None) }
      )
    }

    "accept authorized roles for getAgreementConsumerDocument" in {
      val endpoint = AuthorizedRoutes.endpoints("getAgreementConsumerDocument")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.getAgreementConsumerDocument("agreementId", "documentId") }
      )
    }

    "accept authorized roles for addAgreementConsumerDocument" in {
      val endpoint = AuthorizedRoutes.endpoints("addAgreementConsumerDocument")

      val fakeSeed = DocumentSeed(
        id = UUID.randomUUID(),
        name = "doc1",
        prettyName = "prettyDoc1",
        contentType = "pdf",
        path = "somewhere"
      )

      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] =>
          service.addAgreementConsumerDocument("agreementId", fakeSeed)
        }
      )
    }

    "accept authorized roles for removeAgreementConsumerDocument" in {
      val endpoint = AuthorizedRoutes.endpoints("removeAgreementConsumerDocument")

      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] =>
          service.removeAgreementConsumerDocument("agreementId", "documentId")
        }
      )
    }

    "accept authorized roles for upgradeAgreementById" in {
      val endpoint = AuthorizedRoutes.endpoints("upgradeAgreementById")

      val fakeSeed = UpgradeAgreementSeed(
        descriptorId = UUID.randomUUID(),
        Stamp(who = UUID.randomUUID(), when = OffsetDateTime.now())
      )

      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.upgradeAgreementById("test", fakeSeed) }
      )
    }

  }
}
