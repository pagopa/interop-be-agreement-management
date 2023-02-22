package it.pagopa.interop.agreementmanagement.model.persistence.serializer

import cats.implicits.toTraverseOps
import it.pagopa.interop.agreementmanagement.model.agreement.PersistentAgreement
import it.pagopa.interop.agreementmanagement.model.persistence._
import it.pagopa.interop.agreementmanagement.model.persistence.serializer.v1.events._
import it.pagopa.interop.agreementmanagement.model.persistence.serializer.v1.protobufUtils._
import it.pagopa.interop.agreementmanagement.model.persistence.serializer.v1.state.{AgreementsV1, StateV1}

package object v1 {

  // type alias for traverse type inference
  type ThrowableOr[A] = Either[Throwable, A]

  implicit def stateV1PersistEventDeserializer: PersistEventDeserializer[StateV1, State] =
    state => {
      for {
        agreements <- state.agreements
          .traverse[ThrowableOr, (String, PersistentAgreement)](entry =>
            toPersistentAgreement(entry.value).map(agreement => (entry.key, agreement))
          )
          .map(_.toMap)
      } yield State(agreements)
    }

  implicit def stateV1PersistEventSerializer: PersistEventSerializer[State, StateV1] =
    state => {
      val agreementsV1 = state.agreements.map { case (key, agreement) =>
        AgreementsV1(key, toProtobufAgreement(agreement))
      }.toSeq
      Right(StateV1(agreementsV1))
    }

  implicit def agreementAddedV1PersistEventDeserializer: PersistEventDeserializer[AgreementAddedV1, AgreementAdded] =
    event => toPersistentAgreement(event.agreement).map(AgreementAdded)

  implicit def agreementAddedV1PersistEventSerializer: PersistEventSerializer[AgreementAdded, AgreementAddedV1] =
    event => Right(AgreementAddedV1.of(toProtobufAgreement(event.agreement)))

  implicit def agreementActivatedV1PersistEventDeserializer
    : PersistEventDeserializer[AgreementActivatedV1, AgreementActivated] =
    event => toPersistentAgreement(event.agreement).map(AgreementActivated)

  implicit def agreementActivatedV1PersistEventSerializer
    : PersistEventSerializer[AgreementActivated, AgreementActivatedV1] =
    event => Right(AgreementActivatedV1.of(toProtobufAgreement(event.agreement)))

  implicit def agreementSuspendedV1PersistEventDeserializer
    : PersistEventDeserializer[AgreementSuspendedV1, AgreementSuspended] =
    event => toPersistentAgreement(event.agreement).map(AgreementSuspended)

  implicit def agreementSuspendedV1PersistEventSerializer
    : PersistEventSerializer[AgreementSuspended, AgreementSuspendedV1] =
    event => Right(AgreementSuspendedV1.of(toProtobufAgreement(event.agreement)))

  implicit def agreementDeactivatedV1PersistEventDeserializer
    : PersistEventDeserializer[AgreementDeactivatedV1, AgreementDeactivated] =
    event => toPersistentAgreement(event.agreement).map(AgreementDeactivated)

  implicit def agreementDeactivatedV1PersistEventSerializer
    : PersistEventSerializer[AgreementDeactivated, AgreementDeactivatedV1] =
    event => Right(AgreementDeactivatedV1.of(toProtobufAgreement(event.agreement)))

  implicit def agreementUpdatedV1PersistEventDeserializer
    : PersistEventDeserializer[AgreementUpdatedV1, AgreementUpdated] =
    event => toPersistentAgreement(event.agreement).map(AgreementUpdated)

  implicit def agreementUpdatedV1PersistEventSerializer: PersistEventSerializer[AgreementUpdated, AgreementUpdatedV1] =
    event => Right(AgreementUpdatedV1.of(toProtobufAgreement(event.agreement)))

  implicit def agreementDeletedV1PersistEventDeserializer
    : PersistEventDeserializer[AgreementDeletedV1, AgreementDeleted] =
    event => Right(AgreementDeleted(event.agreementId))

  implicit def agreementDeletedV1PersistEventSerializer: PersistEventSerializer[AgreementDeleted, AgreementDeletedV1] =
    event => Right(AgreementDeletedV1.of(event.agreementId))

  implicit def agreementConsumerDocumentAddedV1PersistEventSerializer
    : PersistEventSerializer[AgreementConsumerDocumentAdded, AgreementConsumerDocumentAddedV1] =
    event => Right(AgreementConsumerDocumentAddedV1.of(event.agreementId, toProtobufDocument(event.document)))

  implicit def agreementConsumerDocumentAddedV1PersistEventDeserializer
    : PersistEventDeserializer[AgreementConsumerDocumentAddedV1, AgreementConsumerDocumentAdded] =
    event => toPersistentDocument(event.document).map(AgreementConsumerDocumentAdded(event.agreementId, _))

  implicit def agreementConsumerDocumentRemovedV1PersistEventSerializer
    : PersistEventSerializer[AgreementConsumerDocumentRemoved, AgreementConsumerDocumentRemovedV1] =
    event => Right(AgreementConsumerDocumentRemovedV1.of(event.agreementId, event.documentId))

  implicit def agreementConsumerDocumentRemovedV1PersistEventDeserializer
    : PersistEventDeserializer[AgreementConsumerDocumentRemovedV1, AgreementConsumerDocumentRemoved] =
    event => Right(AgreementConsumerDocumentRemoved(event.agreementId, event.documentId))

  implicit def verifiedAttributeV1PersistEventDeserializer
    : PersistEventDeserializer[VerifiedAttributeUpdatedV1, VerifiedAttributeUpdated] =
    event => toPersistentAgreement(event.agreement).map(VerifiedAttributeUpdated)

  implicit def verifiedAttributeUpdatedV1PersistEventSerializer
    : PersistEventSerializer[VerifiedAttributeUpdated, VerifiedAttributeUpdatedV1] =
    event => Right(VerifiedAttributeUpdatedV1.of(toProtobufAgreement(event.agreement)))

  implicit def agreementContractAddedV1PersistEventDeserializer
    : PersistEventDeserializer[AgreementContractAddedV1, AgreementContractAdded] =
    event => toPersistentDocument(event.contract).map(contract => AgreementContractAdded(event.agreementId, contract))

  implicit def agreementContractAddedV1PersistEventSerializer
    : PersistEventSerializer[AgreementContractAdded, AgreementContractAddedV1] =
    event => Right(AgreementContractAddedV1.of(event.agreementId, toProtobufDocument(event.contract)))

}
