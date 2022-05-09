package it.pagopa.interop.agreementmanagement.model.agreement

import it.pagopa.interop.agreementmanagement.model._

object PersistentAgreementState
sealed trait PersistentAgreementState
case object Pending   extends PersistentAgreementState
case object Active    extends PersistentAgreementState
case object Suspended extends PersistentAgreementState
case object Inactive  extends PersistentAgreementState