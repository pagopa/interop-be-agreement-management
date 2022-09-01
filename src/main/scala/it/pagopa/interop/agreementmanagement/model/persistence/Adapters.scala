package it.pagopa.interop.agreementmanagement.model.persistence

import it.pagopa.interop.agreementmanagement.model._
import it.pagopa.interop.agreementmanagement.model.agreement._
import it.pagopa.interop.commons.utils.service._

object Adapters {

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
      state = Draft,
      verifiedAttributes = agreement.verifiedAttributes.distinctBy(_.id).map(PersistentVerifiedAttribute.fromAPI),
      certifiedAttributes = agreement.certifiedAttributes.distinctBy(_.id).map(PersistentCertifiedAttribute.fromAPI),
      declaredAttributes = agreement.declaredAttributes.distinctBy(_.id).map(PersistentDeclaredAttribute.fromAPI),
      suspendedByConsumer = None,
      suspendedByProducer = None,
      suspendedByPlatform = None,
      consumerDocuments = Nil,
      createdAt = dateTimeSupplier.get,
      updatedAt = None,
      consumerNotes = agreement.consumerNotes
    )

    def update(
      agreement: PersistentAgreement,
      updateAgreementSeed: UpdateAgreementSeed,
      dateTimeSupplier: OffsetDateTimeSupplier
    ): PersistentAgreement =
      agreement.copy(
        state = PersistentAgreementState.fromApi(updateAgreementSeed.state),
        certifiedAttributes = updateAgreementSeed.certifiedAttributes.map(PersistentCertifiedAttribute.fromAPI),
        declaredAttributes = updateAgreementSeed.declaredAttributes.map(PersistentDeclaredAttribute.fromAPI),
        verifiedAttributes = updateAgreementSeed.verifiedAttributes.map(PersistentVerifiedAttribute.fromAPI),
        suspendedByConsumer = updateAgreementSeed.suspendedByConsumer,
        suspendedByProducer = updateAgreementSeed.suspendedByProducer,
        suspendedByPlatform = updateAgreementSeed.suspendedByPlatform,
        updatedAt = Some(dateTimeSupplier.get),
        consumerNotes = updateAgreementSeed.consumerNotes
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
        state = oldAgreement.state,
        verifiedAttributes = oldAgreement.verifiedAttributes,
        certifiedAttributes = oldAgreement.certifiedAttributes,
        declaredAttributes = oldAgreement.declaredAttributes,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        suspendedByPlatform = None,
        consumerDocuments = oldAgreement.consumerDocuments,
        createdAt = dateTimeSupplier.get,
        updatedAt = None,
        consumerNotes = oldAgreement.consumerNotes
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
      case Draft                      => AgreementState.DRAFT
      case Pending                    => AgreementState.PENDING
      case Active                     => AgreementState.ACTIVE
      case Suspended                  => AgreementState.SUSPENDED
      case Archived                   => AgreementState.ARCHIVED
      case MissingCertifiedAttributes => AgreementState.MISSING_CERTIFIED_ATTRIBUTES
    }
  }

  implicit class PersistentAgreementStateObjectWrapper(private val p: PersistentAgreementState.type) extends AnyVal {
    def fromApi(status: AgreementState): PersistentAgreementState = status match {
      case AgreementState.DRAFT                        => Draft
      case AgreementState.PENDING                      => Pending
      case AgreementState.ACTIVE                       => Active
      case AgreementState.SUSPENDED                    => Suspended
      case AgreementState.ARCHIVED                     => Archived
      case AgreementState.MISSING_CERTIFIED_ATTRIBUTES => MissingCertifiedAttributes
    }
  }

  implicit class PersistentVerifiedAttributeObjectWrapper(private val p: PersistentVerifiedAttribute.type)
      extends AnyVal {
    // Note: It's possible to set documents = Nil because this function is only used when creating a new attribute
    def fromAPI(attribute: AttributeSeed): PersistentVerifiedAttribute            =
      PersistentVerifiedAttribute(id = attribute.id)
    def fromAPI(attribute: VerifiedAttribute): PersistentVerifiedAttribute        =
      PersistentVerifiedAttribute(id = attribute.id)
    def toAPI(persistedAttribute: PersistentVerifiedAttribute): VerifiedAttribute =
      VerifiedAttribute(id = persistedAttribute.id)
  }

  implicit class PersistentCertifiedAttributeObjectWrapper(private val p: PersistentCertifiedAttribute.type)
      extends AnyVal {
    def fromAPI(attribute: AttributeSeed): PersistentCertifiedAttribute             =
      PersistentCertifiedAttribute(id = attribute.id)
    def fromAPI(attribute: CertifiedAttribute): PersistentCertifiedAttribute        =
      PersistentCertifiedAttribute(id = attribute.id)
    def toAPI(persistedAttribute: PersistentCertifiedAttribute): CertifiedAttribute =
      CertifiedAttribute(id = persistedAttribute.id)
  }

  implicit class PersistentDeclaredAttributeObjectWrapper(private val p: PersistentDeclaredAttribute.type)
      extends AnyVal {
    def fromAPI(attribute: AttributeSeed): PersistentDeclaredAttribute = PersistentDeclaredAttribute(id = attribute.id)
    def fromAPI(attribute: DeclaredAttribute): PersistentDeclaredAttribute        =
      PersistentDeclaredAttribute(id = attribute.id)
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
