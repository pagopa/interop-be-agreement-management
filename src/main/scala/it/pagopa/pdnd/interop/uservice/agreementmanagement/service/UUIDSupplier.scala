package it.pagopa.pdnd.interop.uservice.agreementmanagement.service

import java.util.UUID

trait UUIDSupplier {
  def get: UUID
}
