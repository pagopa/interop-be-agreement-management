package it.pagopa.interop.agreementmanagement.error

import it.pagopa.interop.agreementmanagement.model.agreement.PersistentAgreementState
import it.pagopa.interop.commons.utils.errors.ComponentError

object AgreementManagementErrors {
  case class AgreementConflict(agreementId: String)
      extends ComponentError("0001", s"Agreement $agreementId already existing")

  case class AgreementNotFound(agreementId: String) extends ComponentError("0002", s"Agreement $agreementId not found")

  case class AgreementNotInExpectedState(agreementId: String, state: PersistentAgreementState)
      extends ComponentError("0003", s"Agreement $agreementId not in expected state (current state: ${state.toString})")

  case class AgreementDocumentNotFound(agreementId: String, documentId: String)
      extends ComponentError("0005", s"Document $documentId not found for agreement $agreementId")

  case class AgreementDocumentAlreadyExists(agreementId: String)
      extends ComponentError("0006", s"Agreement document for $agreementId already exists")

}
