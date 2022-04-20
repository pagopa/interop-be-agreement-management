package it.pagopa.interop.agreementmanagement.model.persistence

import cats.syntax.all._
import it.pagopa.interop.commons.utils.service._
import java.time.OffsetDateTime
import it.pagopa.interop.agreementmanagement.model._
import it.pagopa.interop.agreementmanagement.model.agreement._
import it.pagopa.interop.agreementmanagement.error.AgreementManagementErrors._

object Adapters {

  implicit class PersistentAgreementWrapper(private val p: PersistentAgreement) {

    val ACTIVABLE_STATES: Set[PersistentAgreementState]   = Set(Pending, Suspended)
    val SUSPENDABLE_STATES: Set[PersistentAgreementState] = Set(Active, Suspended)
    val DEACTIVABLE_STATES: Set[PersistentAgreementState] = Set(Active, Suspended)

    def isActivable: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(p.id.toString, p.state))
      .withRight[Unit]
      .unlessA(ACTIVABLE_STATES.contains(p.state))

    def isSuspendable: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(p.id.toString, p.state))
      .withRight[Unit]
      .unlessA(SUSPENDABLE_STATES.contains(p.state))

    def isDeactivable: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(p.id.toString, p.state))
      .withRight[Unit]
      .unlessA(DEACTIVABLE_STATES.contains(p.state))

  }

  implicit class PersistentAgreementObjectWrapper(private val p: PersistentAgreement.type) extends AnyVal {

    def fromAPI(
      agreement: AgreementSeed,
      uuidSupplier: UUIDSupplier,
      dateTimeSupplier: OffsetDateTimeSupplier
    ): PersistentAgreement =
      PersistentAgreement(
        id = uuidSupplier.get,
        eserviceId = agreement.eserviceId,
        descriptorId = agreement.descriptorId,
        producerId = agreement.producerId,
        consumerId = agreement.consumerId,
        state = Pending,
        verifiedAttributes = agreement.verifiedAttributes.distinctBy(_.id).map(PersistentVerifiedAttribute.fromAPI),
        suspendedByConsumer = None,
        suspendedByProducer = None,
        createdAt = dateTimeSupplier.get,
        updatedAt = None
      )

    def fromAPIWithActiveState(
      agreement: AgreementSeed,
      uuidSupplier: UUIDSupplier,
      dateTimeSupplier: OffsetDateTimeSupplier
    ): PersistentAgreement =
      PersistentAgreement(
        id = uuidSupplier.get,
        eserviceId = agreement.eserviceId,
        descriptorId = agreement.descriptorId,
        producerId = agreement.producerId,
        consumerId = agreement.consumerId,
        state = Active,
        verifiedAttributes = agreement.verifiedAttributes.distinctBy(_.id).map(PersistentVerifiedAttribute.fromAPI),
        suspendedByConsumer = None,
        suspendedByProducer = None,
        createdAt = dateTimeSupplier.get,
        updatedAt = None
      )

    def toAPI(persistentAgreement: PersistentAgreement): Agreement = {
      Agreement(
        id = persistentAgreement.id,
        eserviceId = persistentAgreement.eserviceId,
        descriptorId = persistentAgreement.descriptorId,
        producerId = persistentAgreement.producerId,
        consumerId = persistentAgreement.consumerId,
        state = persistentAgreement.state.toApi,
        verifiedAttributes = persistentAgreement.verifiedAttributes.map(PersistentVerifiedAttribute.toAPI),
        suspendedByConsumer = persistentAgreement.suspendedByConsumer,
        suspendedByProducer = persistentAgreement.suspendedByProducer,
        createdAt = persistentAgreement.createdAt,
        updatedAt = persistentAgreement.updatedAt
      )
    }
  }

  implicit class PersistentAgreementStateWrapper(private val p: PersistentAgreementState) extends AnyVal {
    def toApi: AgreementState = p match {
      case Pending   => AgreementState.PENDING
      case Active    => AgreementState.ACTIVE
      case Suspended => AgreementState.SUSPENDED
      case Inactive  => AgreementState.INACTIVE
    }
  }

  implicit class PersistentAgreementStateObjectWrapper(private val p: PersistentAgreementState.type) extends AnyVal {
    def fromApi(status: AgreementState): PersistentAgreementState = status match {
      case AgreementState.PENDING   => Pending
      case AgreementState.ACTIVE    => Active
      case AgreementState.SUSPENDED => Suspended
      case AgreementState.INACTIVE  => Inactive
    }
  }

  implicit class PersistentVerifiedAttributeObjectWrapper(private val p: PersistentVerifiedAttribute.type)
      extends AnyVal {
    def fromAPI(attribute: VerifiedAttributeSeed): PersistentVerifiedAttribute    = PersistentVerifiedAttribute(
      id = attribute.id,
      verified = attribute.verified,
      verificationDate = attribute.verified match {
        case Some(_) => Some(OffsetDateTime.now())
        case None    => None
      },
      validityTimespan = attribute.validityTimespan
    )
    def toAPI(persistedAttribute: PersistentVerifiedAttribute): VerifiedAttribute =
      VerifiedAttribute(
        id = persistedAttribute.id,
        verified = persistedAttribute.verified,
        verificationDate = persistedAttribute.verificationDate,
        validityTimespan = persistedAttribute.validityTimespan
      )
  }

}
