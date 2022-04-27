package it.pagopa.interop.agreementmanagement.common.system

import com.typesafe.config.{Config, ConfigFactory}

object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  lazy val serverPort: Int = config.getInt("agreement-management.port")

  lazy val jwtAudience: Set[String] = config.getString("agreement-management.jwt.audience").split(",").toSet

  lazy val numberOfProjectionTags: Int = config.getInt("akka.cluster.sharding.number-of-shards")
  def projectionTag(index: Int)        = s"interop-be-agreement-management-persistence|$index"
  lazy val projectionsEnabled: Boolean = config.getBoolean("akka.projection.enabled")
}
