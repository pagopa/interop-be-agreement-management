package it.pagopa.interop.agreementmanagement.model.persistence

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
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
final case class UpdateAgreement(agreement: PersistentAgreement, replyTo: ActorRef[StatusReply[PersistentAgreement]])
    extends Command
final case class DeleteAgreement(agreementId: String, replyTo: ActorRef[StatusReply[Unit]])             extends Command
final case class ListAgreements(
  from: Int,
  to: Int,
  producerId: Option[String],
  consumerId: Option[String],
  eserviceId: Option[String],
  descriptorId: Option[String],
  attributeId: Option[String],
  states: List[PersistentAgreementState],
  replyTo: ActorRef[Seq[PersistentAgreement]]
) extends Command

final case class AddAgreementContract(
  agreementId: String,
  contract: PersistentAgreementDocument,
  replyTo: ActorRef[StatusReply[PersistentAgreementDocument]]
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
