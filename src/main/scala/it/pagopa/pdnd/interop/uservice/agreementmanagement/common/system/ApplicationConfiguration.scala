package it.pagopa.pdnd.interop.uservice.agreementmanagement.common.system

import com.typesafe.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters.ListHasAsScala

object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  lazy val serverPort: Int = config.getInt("uservice-agreement-management.port")

  lazy val jwtAudience: Set[String] = config.getStringList("uservice-agreement-management.jwt.audience").asScala.toSet

}
