package it.pagopa.interop.agreementmanagement.model.persistence

import cats.syntax.all._
import it.pagopa.interop.agreementmanagement.error.AgreementManagementErrors._
import it.pagopa.interop.agreementmanagement.model._
import it.pagopa.interop.agreementmanagement.model.agreement._
import it.pagopa.interop.commons.utils.service._

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
    ): PersistentAgreement = PersistentAgreement(
      id = uuidSupplier.get,
      eserviceId = agreement.eserviceId,
      descriptorId = agreement.descriptorId,
      producerId = agreement.producerId,
      consumerId = agreement.consumerId,
      state = Pending,
      verifiedAttributes = agreement.verifiedAttributes.distinctBy(_.id).map(PersistentVerifiedAttribute.fromAPI),
      certifiedAttributes = agreement.certifiedAttributes.distinctBy(_.id).map(PersistentCertifiedAttribute.fromAPI),
      declaredAttributes = agreement.declaredAttributes.distinctBy(_.id).map(PersistentDeclaredAttribute.fromAPI),
      suspendedByConsumer = None,
      suspendedByProducer = None,
      suspendedByPlatform = None,
      consumerDocuments = Nil,
      createdAt = dateTimeSupplier.get,
      updatedAt = None
    )

    def upgrade(
      oldAgreement: PersistentAgreement,
      seed: UpgradeAgreementSeed
    )(uuidSupplier: UUIDSupplier, dateTimeSupplier: OffsetDateTimeSupplier): PersistentAgreement =
      PersistentAgreement(
        id = uuidSupplier.get,
        eserviceId = oldAgreement.eserviceId,
        descriptorId = seed.descriptorId,
        producerId = oldAgreement.producerId,
        consumerId = oldAgreement.consumerId,
        state = Active,
        verifiedAttributes = oldAgreement.verifiedAttributes,
        certifiedAttributes = oldAgreement.certifiedAttributes,
        declaredAttributes = oldAgreement.declaredAttributes,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        suspendedByPlatform = None,
        consumerDocuments = oldAgreement.consumerDocuments,
        createdAt = dateTimeSupplier.get,
        updatedAt = None
      )

    def toAPI(persistentAgreement: PersistentAgreement): Agreement = Agreement(
      id = persistentAgreement.id,
      eserviceId = persistentAgreement.eserviceId,
      descriptorId = persistentAgreement.descriptorId,
      producerId = persistentAgreement.producerId,
      consumerId = persistentAgreement.consumerId,
      state = persistentAgreement.state.toApi,
      verifiedAttributes = persistentAgreement.verifiedAttributes.map(PersistentVerifiedAttribute.toAPI),
      certifiedAttributes = persistentAgreement.certifiedAttributes.map(PersistentCertifiedAttribute.toAPI),
      declaredAttributes = persistentAgreement.declaredAttributes.map(PersistentDeclaredAttribute.toAPI),
      suspendedByConsumer = persistentAgreement.suspendedByConsumer,
      suspendedByProducer = persistentAgreement.suspendedByProducer,
      suspendedByPlatform = persistentAgreement.suspendedByPlatform,
      consumerDocuments = persistentAgreement.consumerDocuments.map(PersistentAgreementDocument.toAPI),
      createdAt = persistentAgreement.createdAt,
      updatedAt = persistentAgreement.updatedAt
    )
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
    // Note: It's possible to set documents = Nil because this function is only used when creating a new attribute
    def fromAPI(attribute: AttributeSeed): PersistentVerifiedAttribute            =
      PersistentVerifiedAttribute(id = attribute.id)
    def toAPI(persistedAttribute: PersistentVerifiedAttribute): VerifiedAttribute =
      VerifiedAttribute(id = persistedAttribute.id)
  }

  implicit class PersistentCertifiedAttributeObjectWrapper(private val p: PersistentCertifiedAttribute.type)
      extends AnyVal {
    def fromAPI(attribute: AttributeSeed): PersistentCertifiedAttribute             =
      PersistentCertifiedAttribute(id = attribute.id)
    def toAPI(persistedAttribute: PersistentCertifiedAttribute): CertifiedAttribute =
      CertifiedAttribute(id = persistedAttribute.id)
  }

  implicit class PersistentDeclaredAttributeObjectWrapper(private val p: PersistentDeclaredAttribute.type)
      extends AnyVal {
    def fromAPI(attribute: AttributeSeed): PersistentDeclaredAttribute = PersistentDeclaredAttribute(id = attribute.id)
    def toAPI(persistedAttribute: PersistentDeclaredAttribute): DeclaredAttribute =
      DeclaredAttribute(id = persistedAttribute.id)
  }

  implicit class PersistentAgreementDocumentObjectWrapper(private val p: PersistentAgreementDocument.type)
      extends AnyVal {
    def fromAPI(
      seed: DocumentSeed
    )(uuidSupplier: UUIDSupplier, dateTimeSupplier: OffsetDateTimeSupplier): PersistentAgreementDocument =
      PersistentAgreementDocument(
        id = uuidSupplier.get,
        name = seed.name,
        prettyName = seed.prettyName,
        contentType = seed.contentType,
        path = seed.path,
        createdAt = dateTimeSupplier.get
      )
    def toAPI(document: PersistentAgreementDocument): Document =
      Document(
        id = document.id,
        name = document.name,
        prettyName = document.prettyName,
        contentType = document.contentType,
        path = document.path,
        createdAt = document.createdAt
      )
  }

}
