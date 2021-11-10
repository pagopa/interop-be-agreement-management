package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.agreement

sealed trait PersistentStateChangeDetails

object PersistentStateChangeDetails {
  case object Consumer extends PersistentStateChangeDetails
  case object Producer extends PersistentStateChangeDetails
}
