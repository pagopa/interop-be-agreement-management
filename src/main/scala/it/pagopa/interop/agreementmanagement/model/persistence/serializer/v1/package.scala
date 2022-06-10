package it.pagopa.interop.agreementmanagement.model.persistence.serializer

import cats.implicits.toTraverseOps
import it.pagopa.interop.agreementmanagement.model.agreement.PersistentAgreement
import it.pagopa.interop.agreementmanagement.model.persistence._
import it.pagopa.interop.agreementmanagement.model.persistence.serializer.v1.events._
import it.pagopa.interop.agreementmanagement.model.persistence.serializer.v1.protobufUtils.{
  toPersistentAgreement,
  toProtobufAgreement
}
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
      for {
        agreementsV1 <- state.agreements.toSeq.traverse[ThrowableOr, AgreementsV1] { case (key, agreement) =>
          toProtobufAgreement(agreement).map(value => AgreementsV1(key, value))
        }
      } yield StateV1(agreementsV1)
    }

  implicit def agreementAddedV1PersistEventDeserializer: PersistEventDeserializer[AgreementAddedV1, AgreementAdded] =
    event => toPersistentAgreement(event.agreement).map(AgreementAdded)

  implicit def agreementAddedV1PersistEventSerializer: PersistEventSerializer[AgreementAdded, AgreementAddedV1] =
    event => toProtobufAgreement(event.agreement).map(ag => AgreementAddedV1.of(ag))

  implicit def agreementDocumentAddedV1PersistEventDeserializer
    : PersistEventDeserializer[AgreementDocumentAddedV1, AgreementDocumentAdded] =
    event => toPersistentAgreement(event.agreement).map(AgreementDocumentAdded)

  implicit def agreementDocumentAddedV1PersistEventSerializer
    : PersistEventSerializer[AgreementDocumentAdded, AgreementDocumentAddedV1] =
    event => toProtobufAgreement(event.agreement).map(ag => AgreementDocumentAddedV1.of(ag))

  implicit def verifiedAttributeUpdatedV1PersistEventDeserializer
    : PersistEventDeserializer[VerifiedAttributeUpdatedV1, VerifiedAttributeUpdated] =
    event => toPersistentAgreement(event.agreement).map(VerifiedAttributeUpdated)

  implicit def verifiedAttributeUpdatedV1PersistEventSerializer
    : PersistEventSerializer[VerifiedAttributeUpdated, VerifiedAttributeUpdatedV1] =
    event => toProtobufAgreement(event.agreement).map(ag => VerifiedAttributeUpdatedV1.of(ag))

  implicit def agreementActivatedV1PersistEventSerializer
    : PersistEventSerializer[AgreementActivated, AgreementActivatedV1] =
    event => toProtobufAgreement(event.agreement).map(ag => AgreementActivatedV1.of(ag))

  implicit def agreementActivatedV1PersistEventDeserializer
    : PersistEventDeserializer[AgreementActivatedV1, AgreementActivated] =
    event => toPersistentAgreement(event.agreement).map(AgreementActivated)

  implicit def agreementSuspendedV1PersistEventSerializer
    : PersistEventSerializer[AgreementSuspended, AgreementSuspendedV1] =
    event => toProtobufAgreement(event.agreement).map(ag => AgreementSuspendedV1.of(ag))

  implicit def agreementSuspendedV1PersistEventDeserializer
    : PersistEventDeserializer[AgreementSuspendedV1, AgreementSuspended] =
    event => toPersistentAgreement(event.agreement).map(AgreementSuspended)

  implicit def agreementDeactivatedV1PersistEventSerializer
    : PersistEventSerializer[AgreementDeactivated, AgreementDeactivatedV1] =
    event => toProtobufAgreement(event.agreement).map(ag => AgreementDeactivatedV1.of(ag))

  implicit def agreementDeactivatedV1PersistEventDeserializer
    : PersistEventDeserializer[AgreementDeactivatedV1, AgreementDeactivated] =
    event => toPersistentAgreement(event.agreement).map(AgreementDeactivated)

}
