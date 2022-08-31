package it.pagopa.interop.agreementmanagement.model.agreement

sealed trait PersistentStateChangeDetails

object PersistentStateChangeDetails {
  case object Consumer extends PersistentStateChangeDetails
  case object Producer extends PersistentStateChangeDetails
  case object Platform extends PersistentStateChangeDetails
}
