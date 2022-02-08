package it.pagopa.pdnd.interop.uservice.agreementmanagement.common.system

import com.typesafe.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters.ListHasAsScala

object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  def serverPort: Int = config.getInt("uservice-agreement-management.port")

  def jwtAudience: Set[String] = config.getStringList("uservice-party-management.jwt.audience").asScala.toSet

}
