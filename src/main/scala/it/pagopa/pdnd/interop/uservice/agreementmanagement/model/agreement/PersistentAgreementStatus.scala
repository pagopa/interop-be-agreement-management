package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.agreement

import it.pagopa.pdnd.interop.uservice.agreementmanagement.model._

sealed trait PersistentAgreementStatus {
  def toApi: AgreementStatusEnum = this match {
    case PersistentAgreementStatus.Pending   => PENDING
    case PersistentAgreementStatus.Active    => ACTIVE
    case PersistentAgreementStatus.Suspended => SUSPENDED
    case PersistentAgreementStatus.Inactive  => INACTIVE
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
object PersistentAgreementStatus {
  case object Pending   extends PersistentAgreementStatus
  case object Active    extends PersistentAgreementStatus
  case object Suspended extends PersistentAgreementStatus
  case object Inactive  extends PersistentAgreementStatus

  def fromApi(status: AgreementStatusEnum): PersistentAgreementStatus = status match {
    case PENDING   => Pending
    case ACTIVE    => Active
    case SUSPENDED => Suspended
    case INACTIVE  => Inactive
  }
}
