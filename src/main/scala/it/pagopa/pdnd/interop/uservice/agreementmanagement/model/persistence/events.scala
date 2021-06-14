package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence

import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.Agreement

sealed trait Event extends Persistable

final case class AgreementAdded(agreement: Agreement) extends Event
