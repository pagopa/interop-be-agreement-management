package it.pagopa.interop.agreementmanagement.error

import it.pagopa.interop.commons.utils.errors.ComponentError

object AgreementManagementErrors {
  case object AddAgreementConflict   extends ComponentError("0001", "Agreement already existing")
  case object AddAgreementBadRequest extends ComponentError("0002", "Error while creating agreement - bad request")

  case object GetAgreementNotFound   extends ComponentError("0003", "Agreement not found")
  case object GetAgreementBadRequest extends ComponentError("0004", "Error while retrieving agreement - bad request")

  case object ActivateAgreementNotFound extends ComponentError("0005", "Error while activating agreement - not found")
  case object SuspendAgreementNotFound  extends ComponentError("0006", "Error while suspending agreement - not found")

  case object GetAgreementsBadRequest extends ComponentError("0007", "Error while getting agreements - bad request")
  case object AgreementVerifiedAttributeNotFound
      extends ComponentError("0008", "Error while getting updating agreement verified attributes - not found")

  case object UpdateAgreementBadRequest extends ComponentError("0009", "Error while updating agreement - bad request")
}
