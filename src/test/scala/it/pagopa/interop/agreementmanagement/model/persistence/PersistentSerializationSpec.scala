package it.pagopa.interop.agreementmanagement.model.persistence

import cats.implicits._
import com.softwaremill.diffx.Diff
import com.softwaremill.diffx.generic.auto._
import com.softwaremill.diffx.munit.DiffxAssertions
import it.pagopa.interop.agreementmanagement.model.agreement._
import it.pagopa.interop.agreementmanagement.model.persistence.PersistentSerializationSpec._
import it.pagopa.interop.agreementmanagement.model.persistence.serializer._
import it.pagopa.interop.agreementmanagement.model.persistence.serializer.v1.agreement.AgreementStateV1._
import it.pagopa.interop.agreementmanagement.model.persistence.serializer.v1.agreement._
import it.pagopa.interop.agreementmanagement.model.persistence.serializer.v1.events._
import it.pagopa.interop.agreementmanagement.model.persistence.serializer.v1.state._
import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

import java.time.format.DateTimeFormatter
import java.time.{OffsetDateTime, ZoneOffset}
import scala.reflect.runtime.universe.{TypeTag, typeOf}

class PersistentSerializationSpec extends ScalaCheckSuite with DiffxAssertions {

  serdeCheck[State, StateV1](stateGen, _.sorted)
  deserCheck[State, StateV1](stateGen)
  serdeCheck[AgreementAdded, AgreementAddedV1](agreementAddedGen)
  deserCheck[AgreementAdded, AgreementAddedV1](agreementAddedGen)
  serdeCheck[AgreementUpdated, AgreementUpdatedV1](agreementUpdatedGen)
  deserCheck[AgreementUpdated, AgreementUpdatedV1](agreementUpdatedGen)
  serdeCheck[AgreementConsumerDocumentAdded, AgreementConsumerDocumentAddedV1](agreementConsumerDocumentAddedGen)
  deserCheck[AgreementConsumerDocumentAdded, AgreementConsumerDocumentAddedV1](agreementConsumerDocumentAddedGen)
  serdeCheck[AgreementConsumerDocumentRemoved, AgreementConsumerDocumentRemovedV1](agreementConsumerDocumentRemovedGen)
  deserCheck[AgreementConsumerDocumentRemoved, AgreementConsumerDocumentRemovedV1](agreementConsumerDocumentRemovedGen)

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
    Gen.oneOf((Pending, PENDING), (Active, ACTIVE), (Suspended, SUSPENDED), (Archived, ARCHIVED))

  val persistentDocumentGen: Gen[(PersistentAgreementDocument, AgreementDocumentV1)] = for {
    id              <- Gen.uuid
    name            <- stringGen
    prettyName      <- stringGen
    contentType     <- stringGen
    path            <- stringGen
    (vDate, vDateL) <- offsetDatetimeLongGen
  } yield (
    PersistentAgreementDocument(id, name, prettyName, contentType, path, vDate),
    AgreementDocumentV1(id.toString, name, prettyName, contentType, path, vDateL)
  )

  val persistentVerifiedAttributeGen: Gen[(PersistentVerifiedAttribute, VerifiedAttributeV1)] =
    Gen.uuid.map(id => (PersistentVerifiedAttribute(id), VerifiedAttributeV1(id.toString)))

  val persistentCertifiedAttributeGen: Gen[(PersistentCertifiedAttribute, CertifiedAttributeV1)] =
    Gen.uuid.map(id => (PersistentCertifiedAttribute(id), CertifiedAttributeV1(id.toString)))

  val persistentDeclaredAttributeGen: Gen[(PersistentDeclaredAttribute, DeclaredAttributeV1)] =
    Gen.uuid.map(id => (PersistentDeclaredAttribute(id), DeclaredAttributeV1(id.toString)))

  val stampGen: Gen[(PersistentStamp, StampV1)] = {
    for {
      who           <- Gen.uuid
      (when, whenL) <- offsetDatetimeLongGen
    } yield (PersistentStamp(who = who, when = when), StampV1(who = who.toString, when = whenL))
  }

  val stampsGen: Gen[(PersistentStamps, StampsV1)] = {
    for {
      (submission, submissionV1)                     <- Gen.option(stampGen).map(_.separate)
      (activation, activationV1)                     <- Gen.option(stampGen).map(_.separate)
      (rejection, rejectionV1)                       <- Gen.option(stampGen).map(_.separate)
      (suspensionByProducer, suspensionByProducerV1) <- Gen.option(stampGen).map(_.separate)
      (suspensionByConsumer, suspensionByConsumerV1) <- Gen.option(stampGen).map(_.separate)
      (upgrade, upgradeV1)                           <- Gen.option(stampGen).map(_.separate)
      (archiving, archivingV1)                       <- Gen.option(stampGen).map(_.separate)
    } yield (
      PersistentStamps(
        submission = submission,
        activation = activation,
        rejection = rejection,
        suspensionByProducer = suspensionByProducer,
        suspensionByConsumer = suspensionByConsumer,
        upgrade = upgrade,
        archiving = archiving
      ),
      StampsV1(
        submission = submissionV1,
        activation = activationV1,
        rejection = rejectionV1,
        suspensionByProducer = suspensionByProducerV1,
        suspensionByConsumer = suspensionByConsumerV1,
        upgrade = upgradeV1,
        archiving = archivingV1
      )
    )
  }

  val persistentAgreementGen: Gen[(PersistentAgreement, AgreementV1)] = for {
    id                             <- Gen.uuid
    eserviceId                     <- Gen.uuid
    descriptorId                   <- Gen.uuid
    producerId                     <- Gen.uuid
    consumerId                     <- Gen.uuid
    (state, stateV1)               <- persistentAgreementStateGen
    (vattrs, vattrsV1)             <- Gen.listOfN(10, persistentVerifiedAttributeGen).map(_.separate)
    (cattrs, cattrsV1)             <- Gen.listOfN(10, persistentCertifiedAttributeGen).map(_.separate)
    (dattrs, dattrsV1)             <- Gen.listOfN(10, persistentDeclaredAttributeGen).map(_.separate)
    suspendedByConsumer            <- Gen.option(Gen.oneOf(true, false))
    suspendedByProducer            <- Gen.option(Gen.oneOf(true, false))
    suspendedByPlatform            <- Gen.option(Gen.oneOf(true, false))
    (consumerDocs, consumerDocsV1) <- Gen.listOfN(10, persistentDocumentGen).map(_.separate)
    (createdAt, createdAtV1)       <- offsetDatetimeLongGen
    (updatedAt, updatedAtV1)       <- Gen.option(offsetDatetimeLongGen).map(_.separate)
    consumerNotes                  <- Gen.option(stringGen)
    (contract, contractV1)         <- Gen.option(persistentDocumentGen).map(_.separate)
    (stamps, stampsV1)             <- stampsGen
    rejectionReason                <- Gen.option(stringGen)
    (suspendedAt, suspendedAtV1)   <- Gen.option(offsetDatetimeLongGen).map(_.separate)
  } yield (
    PersistentAgreement(
      id = id,
      eserviceId = eserviceId,
      descriptorId = descriptorId,
      producerId = producerId,
      consumerId = consumerId,
      state = state,
      verifiedAttributes = vattrs,
      certifiedAttributes = cattrs,
      declaredAttributes = dattrs,
      suspendedByConsumer = suspendedByConsumer,
      suspendedByProducer = suspendedByProducer,
      suspendedByPlatform = suspendedByPlatform,
      consumerDocuments = consumerDocs,
      createdAt = createdAt,
      updatedAt = updatedAt,
      consumerNotes = consumerNotes,
      contract = contract,
      stamps = stamps,
      rejectionReason = rejectionReason,
      suspendedAt = suspendedAt
    ),
    AgreementV1(
      id = id.toString(),
      eserviceId = eserviceId.toString(),
      descriptorId = descriptorId.toString(),
      producerId = producerId.toString(),
      consumerId = consumerId.toString(),
      state = stateV1,
      verifiedAttributes = vattrsV1,
      certifiedAttributes = cattrsV1,
      declaredAttributes = dattrsV1,
      suspendedByConsumer = suspendedByConsumer,
      suspendedByProducer = suspendedByProducer,
      suspendedByPlatform = suspendedByPlatform,
      consumerDocuments = consumerDocsV1,
      createdAt = createdAtV1,
      updatedAt = updatedAtV1,
      consumerNotes = consumerNotes,
      contract = contractV1,
      stamps = stampsV1.some,
      rejectionReason = rejectionReason,
      suspendedAt = suspendedAtV1
    )
  )

  val stateGen: Gen[(State, StateV1)] =
    Gen.listOfN(10, persistentAgreementGen).map(_.separate).map { case (ags, agsV1) =>
      val state   = State(ags.map(ag => ag.id.toString -> ag).toMap)
      val stateV1 = StateV1(agsV1.map(agV1 => AgreementsV1(agV1.id, agV1)))
      (state, stateV1)
    }

  val agreementAddedGen: Gen[(AgreementAdded, AgreementAddedV1)] = persistentAgreementGen.map { case (a, b) =>
    (AgreementAdded(a), AgreementAddedV1(b))
  }

  val agreementUpdatedGen: Gen[(AgreementUpdated, AgreementUpdatedV1)] = persistentAgreementGen.map { case (a, b) =>
    (AgreementUpdated(a), AgreementUpdatedV1(b))
  }

  val agreementConsumerDocumentAddedGen: Gen[(AgreementConsumerDocumentAdded, AgreementConsumerDocumentAddedV1)] =
    for {
      agreementId  <- Gen.uuid
      (doc, docV1) <- persistentDocumentGen
    } yield (
      AgreementConsumerDocumentAdded(agreementId.toString, doc),
      AgreementConsumerDocumentAddedV1(agreementId.toString, docV1)
    )

  val agreementConsumerDocumentRemovedGen: Gen[(AgreementConsumerDocumentRemoved, AgreementConsumerDocumentRemovedV1)] =
    for {
      agreementId <- Gen.uuid
      documentId  <- Gen.uuid
    } yield (
      AgreementConsumerDocumentRemoved(agreementId.toString, documentId.toString),
      AgreementConsumerDocumentRemovedV1(agreementId.toString, documentId.toString)
    )

  implicit class PimpedStateV1(val stateV1: StateV1) extends AnyVal {
    def sorted: StateV1 = stateV1.copy(stateV1.agreements.sortBy(_.key))
  }

}
