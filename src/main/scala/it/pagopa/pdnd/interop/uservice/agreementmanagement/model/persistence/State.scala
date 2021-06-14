package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence

import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.Agreement

final case class State(agreements: Map[String, Agreement]) extends Persistable {

  def add(agreement: Agreement): State =
    copy(agreements = agreements + (agreement.id.toString -> agreement))

}

object State {
  val empty: State = State(agreements = Map.empty[String, Agreement])
}
