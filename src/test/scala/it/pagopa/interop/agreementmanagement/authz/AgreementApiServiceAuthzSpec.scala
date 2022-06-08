package it.pagopa.interop.agreementmanagement.authz

import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.Entity
import it.pagopa.interop.agreementmanagement.api.impl.AgreementApiServiceImpl
import it.pagopa.interop.agreementmanagement.model.persistence.Command
import it.pagopa.interop.agreementmanagement.server.impl.Main.agreementPersistenceEntity
import it.pagopa.interop.agreementmanagement.util.{AuthorizedRoutes, ClusteredScalatestRouteTest}
import it.pagopa.interop.commons.utils.USER_ROLES
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import org.scalatest.wordspec.AnyWordSpecLike
import it.pagopa.interop.agreementmanagement.api.impl.AgreementApiMarshallerImpl._
import it.pagopa.interop.agreementmanagement.model.{AgreementSeed, StateChangeDetails, VerifiedAttributeSeed}

import java.time.OffsetDateTime
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
        override def get: OffsetDateTime = OffsetDateTime.now()
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

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(contexts.toMap.get(USER_ROLES).toString, endpoint.asRequest, service.addAgreement(fakeSeed))
      })

      // given a fake role, check that its invocation is forbidden
      endpoint.invalidRoles.foreach(contexts => {
        implicit val invalidCtx = contexts
        invalidRoleCheck(invalidCtx.toMap.get(USER_ROLES).toString, endpoint.asRequest, service.addAgreement(fakeSeed))
      })
    }

    "accept authorized roles for getAgreement" in {
      val endpoint = AuthorizedRoutes.endpoints("getAgreement")

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(contexts.toMap.get(USER_ROLES).toString, endpoint.asRequest, service.getAgreement("fake"))
      })

      // given a fake role, check that its invocation is forbidden
      endpoint.invalidRoles.foreach(contexts => {
        implicit val invalidCtx = contexts
        invalidRoleCheck(invalidCtx.toMap.get(USER_ROLES).toString, endpoint.asRequest, service.getAgreement("fake"))
      })
    }

    "accept authorized roles for activateAgreement" in {
      val endpoint = AuthorizedRoutes.endpoints("activateAgreement")

      val fakeStateDetails = StateChangeDetails()

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.activateAgreement("fake", fakeStateDetails)
        )
      })

      // given a fake role, check that its invocation is forbidden
      endpoint.invalidRoles.foreach(contexts => {
        implicit val invalidCtx = contexts
        invalidRoleCheck(
          invalidCtx.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.activateAgreement("fake", fakeStateDetails)
        )
      })
    }

    "accept authorized roles for suspendAgreement" in {
      val endpoint = AuthorizedRoutes.endpoints("suspendAgreement")

      val fakeStateDetails = StateChangeDetails()

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.suspendAgreement("fake", fakeStateDetails)
        )
      })

      // given a fake role, check that its invocation is forbidden
      endpoint.invalidRoles.foreach(contexts => {
        implicit val invalidCtx = contexts
        invalidRoleCheck(
          invalidCtx.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.suspendAgreement("fake", fakeStateDetails)
        )
      })
    }

    "accept authorized roles for getAgreements" in {
      val endpoint = AuthorizedRoutes.endpoints("getAgreements")

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.getAgreements(None, None, None, None, None)
        )
      })

      // given a fake role, check that its invocation is forbidden
      endpoint.invalidRoles.foreach(contexts => {
        implicit val invalidCtx = contexts
        invalidRoleCheck(
          invalidCtx.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.getAgreements(None, None, None, None, None)
        )
      })
    }

    "accept authorized roles for updateAgreementVerifiedAttribute" in {
      val endpoint = AuthorizedRoutes.endpoints("updateAgreementVerifiedAttribute")

      val fakeSeed = VerifiedAttributeSeed(id = UUID.randomUUID())

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.updateAgreementVerifiedAttribute("test", fakeSeed)
        )
      })

      // given a fake role, check that its invocation is forbidden
      endpoint.invalidRoles.foreach(contexts => {
        implicit val invalidCtx = contexts
        invalidRoleCheck(
          invalidCtx.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.updateAgreementVerifiedAttribute("test", fakeSeed)
        )
      })
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

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.upgradeAgreementById("test", fakeSeed)
        )
      })

      // given a fake role, check that its invocation is forbidden
      endpoint.invalidRoles.foreach(contexts => {
        implicit val invalidCtx = contexts
        invalidRoleCheck(
          invalidCtx.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.upgradeAgreementById("test", fakeSeed)
        )
      })
    }

  }
}
