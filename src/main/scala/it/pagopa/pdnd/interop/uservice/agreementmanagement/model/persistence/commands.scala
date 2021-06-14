package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.Agreement

sealed trait Command

case object Idle                                                                                      extends Command
final case class AddAgreement(agreement: Agreement, replyTo: ActorRef[StatusReply[Agreement]])        extends Command
final case class GetAgreement(agreementId: String, replyTo: ActorRef[StatusReply[Option[Agreement]]]) extends Command
final case class ListAgreements(
  from: Int,
  to: Int,
  producerId: Option[String],
  consumerId: Option[String],
  status: Option[String],
  replyTo: ActorRef[Seq[Agreement]]
) extends Command
