package it.pagopa.interop.agreementmanagement.model.persistence

import cats.implicits._
import org.scalacheck.Prop.forAll
import org.scalacheck.Gen
import munit.ScalaCheckSuite
import PersistentSerializationSpec._

import java.time.{OffsetDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter
import it.pagopa.interop.agreementmanagement.model.agreement._
import it.pagopa.interop.agreementmanagement.model.persistence.serializer.v1.agreement._
import it.pagopa.interop.agreementmanagement.model.persistence.serializer.v1.agreement.AgreementStateV1._
import it.pagopa.interop.agreementmanagement.model.persistence.serializer.v1.state._
import it.pagopa.interop.agreementmanagement.model.persistence.serializer.v1.events._
import it.pagopa.interop.agreementmanagement.model.persistence.serializer._
import com.softwaremill.diffx.munit.DiffxAssertions
import com.softwaremill.diffx.generic.auto._
import com.softwaremill.diffx.Diff

import scala.reflect.runtime.universe.{TypeTag, typeOf}

class PersistentSerializationSpec extends ScalaCheckSuite with DiffxAssertions {

  serdeCheck[State, StateV1](stateGen, _.sorted)
  deserCheck[State, StateV1](stateGen)
  serdeCheck[AgreementAdded, AgreementAddedV1](agreementAddedGen)
  deserCheck[AgreementAdded, AgreementAddedV1](agreementAddedGen)
  serdeCheck[AgreementActivated, AgreementActivatedV1](agreementActivatedGen)
  deserCheck[AgreementActivated, AgreementActivatedV1](agreementActivatedGen)
  serdeCheck[AgreementSuspended, AgreementSuspendedV1](agreementSuspendedGen)
  deserCheck[AgreementSuspended, AgreementSuspendedV1](agreementSuspendedGen)
  serdeCheck[AgreementDeactivated, AgreementDeactivatedV1](agreementDeactivatedGen)
  deserCheck[AgreementDeactivated, AgreementDeactivatedV1](agreementDeactivatedGen)
  serdeCheck[VerifiedAttributeUpdated, VerifiedAttributeUpdatedV1](verifiedAttributeUpdatedGen)
  deserCheck[VerifiedAttributeUpdated, VerifiedAttributeUpdatedV1](verifiedAttributeUpdatedGen)

  // TODO move me in commons
  def serdeCheck[A: TypeTag, B](gen: Gen[(A, B)], adapter: B => B = identity[B](_))(implicit
    e: PersistEventSerializer[A, B],
    loc: munit.Location,
    d: => Diff[Either[Throwable, B]]
  ): Unit = property(s"${typeOf[A].typeSymbol.name.toString} is correctly serialized") {
    forAll(gen) { case (state, stateV1) =>
      implicit val diffX: Diff[Either[Throwable, B]] = d
      assertEqual(PersistEventSerializer.to[A, B](state).map(adapter), Right(stateV1).map(adapter))
    }
  }

  // TODO move me in commons
  def deserCheck[A, B: TypeTag](
    gen: Gen[(A, B)]
  )(implicit e: PersistEventDeserializer[B, A], loc: munit.Location, d: => Diff[Either[Throwable, A]]): Unit =
    property(s"${typeOf[B].typeSymbol.name.toString} is correctly deserialized") {
      forAll(gen) { case (state, stateV1) =>
        // * This is declared lazy in the signature to avoid a MethodTooBigException
        implicit val diffX: Diff[Either[Throwable, A]] = d
        assertEqual(PersistEventDeserializer.from[B, A](stateV1), Right(state))
      }
    }
}

object PersistentSerializationSpec {

  val stringGen: Gen[String] = for {
    n <- Gen.chooseNum(4, 100)
    s <- Gen.containerOfN[List, Char](n, Gen.alphaNumChar)
  } yield s.foldLeft("")(_ + _)

  val offsetDatetimeStringGen: Gen[(OffsetDateTime, String)] = for {
    n <- Gen.chooseNum(0, 10000L)
    now = OffsetDateTime.now(ZoneOffset.UTC)
    time <- Gen.oneOf(now.minusSeconds(n), now.plusSeconds(n))
  } yield (time, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(time))

  val offsetDatetimeLongGen: Gen[(OffsetDateTime, Long)] = for {
    n <- Gen.chooseNum(0, 10000L)
    now      = OffsetDateTime.now(ZoneOffset.UTC)
    // Truncate to millis precision
    nowMills = now.withNano(now.getNano - (now.getNano % 1000000))
    time <- Gen.oneOf(nowMills.minusSeconds(n), nowMills.plusSeconds(n))
  } yield (time, time.toInstant.toEpochMilli)

  val persistentAgreementStateGen: Gen[(PersistentAgreementState, AgreementStateV1)] =
    Gen.oneOf((Pending, PENDING), (Active, ACTIVE), (Suspended, SUSPENDED), (Inactive, INACTIVE))

  val persistentVerifiedAttributeGen: Gen[(PersistentVerifiedAttribute, VerifiedAttributeV1)] = for {
    id               <- Gen.uuid
    verified         <- Gen.option(Gen.oneOf(true, false))
    (vDate, vDateS)  <- Gen.option(offsetDatetimeStringGen).map(_.separate)
    validityTimespan <- Gen.option(Gen.posNum[Long])
  } yield (
    PersistentVerifiedAttribute(id, verified, vDate, validityTimespan),
    VerifiedAttributeV1(id.toString(), verified, vDateS, validityTimespan.map(_.toString()))
  )

  val persistentAgreementGen: Gen[(PersistentAgreement, AgreementV1)] = for {
    id                       <- Gen.uuid
    eserviceId               <- Gen.uuid
    descriptorId             <- Gen.uuid
    producerId               <- Gen.uuid
    consumerId               <- Gen.uuid
    (state, stateV1)         <- persistentAgreementStateGen
    (vattrs, vattrsV1)       <- Gen.listOf(persistentVerifiedAttributeGen).map(_.separate)
    suspendedByConsumer      <- Gen.option(Gen.oneOf(true, false))
    suspendedByProducer      <- Gen.option(Gen.oneOf(true, false))
    suspendedByPlatform      <- Gen.option(Gen.oneOf(true, false))
    (createdAt, createdAtV1) <- offsetDatetimeLongGen
    (updatedAt, updatedAtV1) <- Gen.option(offsetDatetimeLongGen).map(_.separate)
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
      suspendedByPlatform = suspendedByPlatform,
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
      suspendedByPlatform = suspendedByPlatform,
      createdAt = createdAtV1,
      updatedAt = updatedAtV1
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
