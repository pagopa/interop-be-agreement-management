package it.pagopa.interop.agreementmanagement.model.persistence

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import it.pagopa.interop.agreementmanagement.model.agreement.{PersistentAgreement, PersistentAgreementState}
import it.pagopa.interop.agreementmanagement.model.{Agreement, StateChangeDetails, VerifiedAttributeSeed}

sealed trait Command

case object Idle                                                                                         extends Command
final case class AddAgreement(agreement: PersistentAgreement, replyTo: ActorRef[StatusReply[Agreement]]) extends Command
final case class UpdateVerifiedAttribute(
  agreementId: String,
  verifiedAttribute: VerifiedAttributeSeed,
  replyTo: ActorRef[StatusReply[Agreement]]
)                                                                                                     extends Command
final case class GetAgreement(agreementId: String, replyTo: ActorRef[StatusReply[Option[Agreement]]]) extends Command
final case class ActivateAgreement(
  agreementId: String,
  stateChangeDetails: StateChangeDetails,
  replyTo: ActorRef[StatusReply[Agreement]]
) extends Command
final case class SuspendAgreement(
  agreementId: String,
  stateChangeDetails: StateChangeDetails,
  replyTo: ActorRef[StatusReply[Agreement]]
) extends Command

final case class DeactivateAgreement(
  agreementId: String,
  stateChangeDetails: StateChangeDetails,
  replyTo: ActorRef[StatusReply[Agreement]]
) extends Command

final case class ListAgreements(
  from: Int,
  to: Int,
  producerId: Option[String],
  consumerId: Option[String],
  eserviceId: Option[String],
  descriptorId: Option[String],
  state: Option[PersistentAgreementState],
  replyTo: ActorRef[Seq[Agreement]]
) extends Command
