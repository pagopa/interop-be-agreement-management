package it.pagopa.interop.agreementmanagement.model.persistence

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import it.pagopa.interop.agreementmanagement.model.StateChangeDetails
import it.pagopa.interop.agreementmanagement.model.agreement.{
  PersistentAgreement,
  PersistentAgreementDocument,
  PersistentAgreementState
}

sealed trait Command

case object Idle                                                                                        extends Command
final case class AddAgreement(agreement: PersistentAgreement, replyTo: ActorRef[StatusReply[PersistentAgreement]])
    extends Command
final case class GetAgreement(agreementId: String, replyTo: ActorRef[StatusReply[PersistentAgreement]]) extends Command
final case class DeleteAgreement(agreementId: String, replyTo: ActorRef[StatusReply[Unit]])             extends Command
final case class ActivateAgreement(
  agreementId: String,
  stateChangeDetails: StateChangeDetails,
  replyTo: ActorRef[StatusReply[PersistentAgreement]]
) extends Command

final case class SubmitAgreement(
  agreementId: String,
  stateChangeDetails: StateChangeDetails,
  replyTo: ActorRef[StatusReply[PersistentAgreement]]
) extends Command

final case class SuspendAgreement(
  agreementId: String,
  stateChangeDetails: StateChangeDetails,
  replyTo: ActorRef[StatusReply[PersistentAgreement]]
) extends Command

final case class DeactivateAgreement(
  agreementId: String,
  stateChangeDetails: StateChangeDetails,
  replyTo: ActorRef[StatusReply[PersistentAgreement]]
) extends Command

final case class ListAgreements(
  from: Int,
  to: Int,
  producerId: Option[String],
  consumerId: Option[String],
  eserviceId: Option[String],
  descriptorId: Option[String],
  state: Option[PersistentAgreementState],
  replyTo: ActorRef[Seq[PersistentAgreement]]
) extends Command

final case class AddAgreementConsumerDocument(
  agreementId: String,
  document: PersistentAgreementDocument,
  replyTo: ActorRef[StatusReply[PersistentAgreementDocument]]
) extends Command

final case class RemoveAgreementConsumerDocument(
  agreementId: String,
  documentId: String,
  replyTo: ActorRef[StatusReply[Unit]]
) extends Command

final case class GetAgreementConsumerDocument(
  agreementId: String,
  documentId: String,
  replyTo: ActorRef[StatusReply[PersistentAgreementDocument]]
) extends Command
