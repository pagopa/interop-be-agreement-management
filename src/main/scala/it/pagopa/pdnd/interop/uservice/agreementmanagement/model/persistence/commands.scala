package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.agreement.PersistentAgreement
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.{Agreement, StatusChangeDetails, VerifiedAttributeSeed}

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
  statusChangeDetails: StatusChangeDetails,
  replyTo: ActorRef[StatusReply[Agreement]]
) extends Command
final case class SuspendAgreement(
  agreementId: String,
  statusChangeDetails: StatusChangeDetails,
  replyTo: ActorRef[StatusReply[Agreement]]
) extends Command
final case class ListAgreements(
  from: Int,
  to: Int,
  producerId: Option[String],
  consumerId: Option[String],
  eserviceId: Option[String],
  descriptorId: Option[String],
  status: Option[String],
  replyTo: ActorRef[Seq[Agreement]]
) extends Command
