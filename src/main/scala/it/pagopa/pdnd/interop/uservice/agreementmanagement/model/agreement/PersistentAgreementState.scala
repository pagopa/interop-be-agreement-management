package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.agreement

import it.pagopa.pdnd.interop.uservice.agreementmanagement.model._

sealed trait PersistentAgreementState {
  def toApi: AgreementState = this match {
    case PersistentAgreementState.Pending   => AgreementState.PENDING
    case PersistentAgreementState.Active    => AgreementState.ACTIVE
    case PersistentAgreementState.Suspended => AgreementState.SUSPENDED
    case PersistentAgreementState.Inactive  => AgreementState.INACTIVE
  }
}
@SuppressWarnings(
  Array(
    "org.wartremover.warts.Any",
    "org.wartremover.warts.Nothing",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.ToString"
  )
)
object PersistentAgreementState {
  case object Pending   extends PersistentAgreementState
  case object Active    extends PersistentAgreementState
  case object Suspended extends PersistentAgreementState
  case object Inactive  extends PersistentAgreementState

  def fromApi(status: AgreementState): PersistentAgreementState = status match {
    case AgreementState.PENDING   => Pending
    case AgreementState.ACTIVE    => Active
    case AgreementState.SUSPENDED => Suspended
    case AgreementState.INACTIVE  => Inactive
  }
}
