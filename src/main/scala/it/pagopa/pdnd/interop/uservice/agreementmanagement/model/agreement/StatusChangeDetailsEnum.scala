package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.agreement

sealed trait StatusChangeDetailsEnum

object StatusChangeDetailsEnum {
  case object Consumer extends StatusChangeDetailsEnum
  case object Producer extends StatusChangeDetailsEnum
}
