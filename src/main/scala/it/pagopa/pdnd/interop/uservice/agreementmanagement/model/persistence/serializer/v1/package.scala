package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence.serializer

import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.Agreement
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence.{AgreementAdded, State}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence.serializer.v1.agreement.AgreementV1
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence.serializer.v1.events.AgreementAddedV1
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence.serializer.v1.state.{AgreementsV1, StateV1}

import java.util.UUID

package object v1 {

  @SuppressWarnings(Array("org.wartremover.warts.Nothing"))
  implicit def stateV1PersistEventDeserializer: PersistEventDeserializer[StateV1, State] =
    state => {
      val agreements = state.agreements
        .map(agreementsV1 =>
          (
            agreementsV1.key,
            Agreement(
              id = UUID.fromString(agreementsV1.value.id),
              eserviceId = UUID.fromString(agreementsV1.value.eserviceId),
              producerId = UUID.fromString(agreementsV1.value.producerId),
              consumerId = UUID.fromString(agreementsV1.value.consumerId),
              status = agreementsV1.value.status
            )
          )
        )
        .toMap
      Right(State(agreements))
    }

  @SuppressWarnings(Array("org.wartremover.warts.Nothing", "org.wartremover.warts.OptionPartial"))
  implicit def stateV1PersistEventSerializer: PersistEventSerializer[State, StateV1] =
    state => {
      val agreements = state.agreements
      val agreementsV1 = agreements.map { case (key, agreement) =>
        AgreementsV1(
          key,
          AgreementV1(
            id = agreement.id.toString,
            eserviceId = agreement.eserviceId.toString,
            producerId = agreement.producerId.toString,
            consumerId = agreement.consumerId.toString,
            status = agreement.status
          )
        )
      }.toSeq
      Right(StateV1(agreementsV1))
    }

  implicit def agreementAddedV1PersistEventDeserializer: PersistEventDeserializer[AgreementAddedV1, AgreementAdded] =
    event =>
      Right[Throwable, AgreementAdded](
        AgreementAdded(agreement =
          Agreement(
            id = UUID.fromString(event.agreement.id),
            eserviceId = UUID.fromString(event.agreement.eserviceId),
            producerId = UUID.fromString(event.agreement.producerId),
            consumerId = UUID.fromString(event.agreement.consumerId),
            status = event.agreement.status
          )
        )
      )

  implicit def agreementAddedV1PersistEventSerializer: PersistEventSerializer[AgreementAdded, AgreementAddedV1] =
    event =>
      Right[Throwable, AgreementAddedV1](
        AgreementAddedV1
          .of(
            AgreementV1(
              id = event.agreement.id.toString,
              eserviceId = event.agreement.eserviceId.toString,
              producerId = event.agreement.producerId.toString,
              consumerId = event.agreement.consumerId.toString,
              status = event.agreement.status
            )
          )
      )

}
