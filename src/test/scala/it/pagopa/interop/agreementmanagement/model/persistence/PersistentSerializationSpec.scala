package it.pagopa.interop.agreementmanagement.model.persistence

import cats.implicits._
import org.scalacheck.Prop.forAll
import org.scalacheck.Gen

import munit.ScalaCheckSuite
import PersistentSerializationSpec._
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import it.pagopa.interop.agreementmanagement.model.agreement._
import it.pagopa.interop.agreementmanagement.model.persistence.serializer.v1.agreement._
import it.pagopa.interop.agreementmanagement.model.persistence.serializer.v1.agreement.AgreementStateV1._
import it.pagopa.interop.agreementmanagement.model.persistence.serializer.v1.state._
import it.pagopa.interop.agreementmanagement.model.persistence.serializer.v1.events._
import it.pagopa.interop.agreementmanagement.model.persistence.serializer._
import com.softwaremill.diffx.munit.DiffxAssertions._
import com.softwaremill.diffx.generic.auto._

class PersistentSerializationSpec extends ScalaCheckSuite {

  property("State is correctly deserialized") {
    forAll(stateGen) { case (state, stateV1) =>
      assertEqual(PersistEventDeserializer.from[StateV1, State](stateV1), Right(state))
    }
  }

  property("AgreementAdded is correctly deserialized") {
    forAll(agreementAddedGen) { case (state, stateV1) =>
      assertEqual(PersistEventDeserializer.from[AgreementAddedV1, AgreementAdded](stateV1), Right(state))
    }
  }

  property("AgreementActivated is correctly deserialized") {
    forAll(agreementActivatedGen) { case (state, stateV1) =>
      assertEqual(PersistEventDeserializer.from[AgreementActivatedV1, AgreementActivated](stateV1), Right(state))
    }
  }

  property("AgreementSuspended is correctly deserialized") {
    forAll(agreementSuspendedGen) { case (state, stateV1) =>
      assertEqual(PersistEventDeserializer.from[AgreementSuspendedV1, AgreementSuspended](stateV1), Right(state))
    }
  }

  property("AgreementDeactivated is correctly deserialized") {
    forAll(agreementDeactivatedGen) { case (state, stateV1) =>
      assertEqual(PersistEventDeserializer.from[AgreementDeactivatedV1, AgreementDeactivated](stateV1), Right(state))
    }
  }

  property("VerifiedAttributeUpdated is correctly deserialized") {
    forAll(verifiedAttributeUpdatedGen) { case (state, stateV1) =>
      assertEqual(
        PersistEventDeserializer.from[VerifiedAttributeUpdatedV1, VerifiedAttributeUpdated](stateV1),
        Right(state)
      )
    }
  }

  property("State is correctly serialized") {
    forAll(stateGen) { case (state, stateV1) =>
      assertEqual(PersistEventSerializer.to[State, StateV1](state).map(_.sorted), Right(stateV1.sorted))
    }
  }

  property("AgreementAdded is correctly serialized") {
    forAll(agreementAddedGen) { case (state, stateV1) =>
      assertEqual(PersistEventSerializer.to[AgreementAdded, AgreementAddedV1](state), Right(stateV1))
    }
  }

  property("AgreementActivated is correctly serialized") {
    forAll(agreementActivatedGen) { case (state, stateV1) =>
      assertEqual(PersistEventSerializer.to[AgreementActivated, AgreementActivatedV1](state), Right(stateV1))
    }
  }

  property("AgreementSuspended is correctly serialized") {
    forAll(agreementSuspendedGen) { case (state, stateV1) =>
      assertEqual(PersistEventSerializer.to[AgreementSuspended, AgreementSuspendedV1](state), Right(stateV1))
    }
  }

  property("AgreementDeactivated is correctly serialized") {
    forAll(agreementDeactivatedGen) { case (state, stateV1) =>
      assertEqual(PersistEventSerializer.to[AgreementDeactivated, AgreementDeactivatedV1](state), Right(stateV1))
    }
  }

  property("VerifiedAttributeUpdated is correctly serialized") {
    forAll(verifiedAttributeUpdatedGen) { case (state, stateV1) =>
      assertEqual(
        PersistEventSerializer.to[VerifiedAttributeUpdated, VerifiedAttributeUpdatedV1](state),
        Right(stateV1)
      )
    }
  }

}

object PersistentSerializationSpec {

  val stringGen: Gen[String] = for {
    n <- Gen.chooseNum(4, 100)
    s <- Gen.containerOfN[List, Char](n, Gen.alphaNumChar)
  } yield s.foldLeft("")(_ + _)

  val offsetDatetimeGen: Gen[(OffsetDateTime, String)] = for {
    n <- Gen.chooseNum(0, 10000L)
    now = OffsetDateTime.now()
    time <- Gen.oneOf(now.minusSeconds(n), now.plusSeconds(n))
  } yield (time, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(time))

  val persistentAgreementStateGen: Gen[(PersistentAgreementState, AgreementStateV1)] =
    Gen.oneOf((Pending, PENDING), (Active, ACTIVE), (Suspended, SUSPENDED), (Inactive, INACTIVE))

  val persistentVerifiedAttributeGen: Gen[(PersistentVerifiedAttribute, VerifiedAttributeV1)] = for {
    id               <- Gen.uuid
    verified         <- Gen.option(Gen.oneOf(true, false))
    (vDate, vDateS)  <- Gen.option(offsetDatetimeGen).map(_.separate)
    validityTimespan <- Gen.option(Gen.posNum[Long])
  } yield (
    PersistentVerifiedAttribute(id, verified, vDate, validityTimespan),
    VerifiedAttributeV1(id.toString(), verified, vDateS, validityTimespan.map(_.toString()))
  )

  val persistentAgreementGen: Gen[(PersistentAgreement, AgreementV1)] = for {
    id                      <- Gen.uuid
    eserviceId              <- Gen.uuid
    descriptorId            <- Gen.uuid
    producerId              <- Gen.uuid
    consumerId              <- Gen.uuid
    (state, stateV1)        <- persistentAgreementStateGen
    (vattrs, vattrsV1)      <- Gen.listOf(persistentVerifiedAttributeGen).map(_.separate)
    suspendedByConsumer     <- Gen.option(Gen.oneOf(true, false))
    suspendedByProducer     <- Gen.option(Gen.oneOf(true, false))
    (createdAt, createdAtS) <- offsetDatetimeGen
    (updatedAt, updatedAtS) <- Gen.option(offsetDatetimeGen).map(_.separate)
  } yield (
    PersistentAgreement(
      id = id,
      eserviceId = eserviceId,
      descriptorId = descriptorId,
      producerId = producerId,
      consumerId = consumerId,
      state = state,
      verifiedAttributes = vattrs,
      suspendedByConsumer = suspendedByConsumer,
      suspendedByProducer = suspendedByProducer,
      createdAt = createdAt,
      updatedAt = updatedAt
    ),
    AgreementV1(
      id = id.toString(),
      eserviceId = eserviceId.toString(),
      descriptorId = descriptorId.toString(),
      producerId = producerId.toString(),
      consumerId = consumerId.toString(),
      state = stateV1,
      verifiedAttributes = vattrsV1,
      suspendedByConsumer = suspendedByConsumer,
      suspendedByProducer = suspendedByProducer,
      createdAt = createdAtS,
      updatedAt = updatedAtS
    )
  )

  val stateGen: Gen[(State, StateV1)] = Gen.listOf(persistentAgreementGen).map(_.separate).map { case (ags, agsV1) =>
    val state   = State(ags.map(ag => (ag.id.toString -> ag)).toMap)
    val stateV1 = StateV1(agsV1.map(agV1 => AgreementsV1(agV1.id, agV1)))
    (state, stateV1)
  }

  val agreementAddedGen: Gen[(AgreementAdded, AgreementAddedV1)] = persistentAgreementGen.map { case (a, b) =>
    (AgreementAdded(a), AgreementAddedV1(b))
  }

  val agreementActivatedGen: Gen[(AgreementActivated, AgreementActivatedV1)] = persistentAgreementGen.map {
    case (a, b) => (AgreementActivated(a), AgreementActivatedV1(b))
  }

  val agreementSuspendedGen: Gen[(AgreementSuspended, AgreementSuspendedV1)] = persistentAgreementGen.map {
    case (a, b) => (AgreementSuspended(a), AgreementSuspendedV1(b))
  }

  val agreementDeactivatedGen: Gen[(AgreementDeactivated, AgreementDeactivatedV1)] = persistentAgreementGen.map {
    case (a, b) => (AgreementDeactivated(a), AgreementDeactivatedV1(b))
  }

  val verifiedAttributeUpdatedGen: Gen[(VerifiedAttributeUpdated, VerifiedAttributeUpdatedV1)] =
    persistentAgreementGen.map { case (a, b) => (VerifiedAttributeUpdated(a), VerifiedAttributeUpdatedV1(b)) }

  implicit class PimpedStateV1(val stateV1: StateV1) extends AnyVal {
    def sorted: StateV1 = stateV1.copy(stateV1.agreements.sortBy(_.key))
  }

}
