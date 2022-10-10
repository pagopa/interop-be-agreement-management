package it.pagopa.interop.agreementmanagement.model.agreement

object PersistentAgreementState
sealed trait PersistentAgreementState
case object Draft                      extends PersistentAgreementState
case object Pending                    extends PersistentAgreementState
case object Active                     extends PersistentAgreementState
case object Suspended                  extends PersistentAgreementState
case object Archived                   extends PersistentAgreementState
case object MissingCertifiedAttributes extends PersistentAgreementState
case object Rejected                   extends PersistentAgreementState
