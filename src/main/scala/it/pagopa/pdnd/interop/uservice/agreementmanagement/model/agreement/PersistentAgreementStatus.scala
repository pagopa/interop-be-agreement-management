package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.agreement

sealed trait PersistentAgreementStatus {
  def stringify: String = this match {
    case PersistentAgreementStatus.Pending   => "pending"
    case PersistentAgreementStatus.Active    => "active"
    case PersistentAgreementStatus.Suspended => "suspended"
    case PersistentAgreementStatus.Inactive  => "inactive"
  }
}

object PersistentAgreementStatus {
  case object Pending   extends PersistentAgreementStatus
  case object Active    extends PersistentAgreementStatus
  case object Suspended extends PersistentAgreementStatus
  case object Inactive  extends PersistentAgreementStatus
  def fromText(str: String): Either[Throwable, PersistentAgreementStatus] = str match {
    case "pending"   => Right(Pending)
    case "active"    => Right(Active)
    case "suspended" => Right(Suspended)
    case "inactive"  => Right(Inactive)
    case _           => Left(new RuntimeException("Deserialization from protobuf failed"))
  }
}
