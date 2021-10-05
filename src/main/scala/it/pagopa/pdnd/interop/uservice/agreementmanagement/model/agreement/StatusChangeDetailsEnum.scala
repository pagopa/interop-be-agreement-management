package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.agreement

sealed trait StatusChangeDetailsEnum {
  def stringify: String = this match {
    case StatusChangeDetailsEnum.Consumer => "consumer"
    case StatusChangeDetailsEnum.Producer => "producer"
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
object StatusChangeDetailsEnum {
  case object Consumer extends StatusChangeDetailsEnum
  case object Producer extends StatusChangeDetailsEnum
  def fromText(str: String): Either[Throwable, StatusChangeDetailsEnum] = str match {
    case "consumer" => Right(Consumer)
    case "producer" => Right(Producer)
    case _          => Left(new RuntimeException("Deserialization from protobuf failed"))
  }
}
