package it.pagopa.pdnd.interop.uservice.agreementmanagement.common.system

import com.typesafe.config.{Config, ConfigFactory}

object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  def serverPort: Int = {
    config.getInt("uservice-agreement-management.port")
  }
}
