package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.agreement

sealed trait PersistentAgreementStatus {
  def stringify: String = this match {
    case PersistentAgreementStatus.Pending   => "pending"
    case PersistentAgreementStatus.Active    => "active"
    case PersistentAgreementStatus.Suspended => "suspended"
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
  def fromText(str: String): Either[Throwable, PersistentAgreementStatus] = str match {
    case "pending"   => Right(Pending)
    case "active"    => Right(Active)
    case "suspended" => Right(Suspended)
    case _           => Left(new RuntimeException("Deserialization from protobuf failed"))
  }
}
