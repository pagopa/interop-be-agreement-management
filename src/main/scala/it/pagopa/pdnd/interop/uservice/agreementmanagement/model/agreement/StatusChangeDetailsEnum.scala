package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.agreement

sealed trait StatusChangeDetailsEnum

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
}
