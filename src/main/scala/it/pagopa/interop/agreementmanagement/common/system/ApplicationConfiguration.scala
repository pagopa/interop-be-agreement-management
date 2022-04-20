package it.pagopa.interop.agreementmanagement.common.system

import com.typesafe.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters.ListHasAsScala

object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  lazy val serverPort: Int          = config.getInt("agreement-management.port")
  lazy val jwtAudience: Set[String] = config.getStringList("agreement-management.jwt.audience").asScala.toSet
  lazy val queueUrl: String         = config.getString("agreement-management.queue-url")

  lazy val numberOfProjectionTags: Int = config.getInt("akka.cluster.sharding.number-of-shards")
  def projectionTag(index: Int)        = s"interop-be-agreement-management-persistence|$index"
  lazy val projectionsEnabled: Boolean = config.getBoolean("akka.projection.enabled")
}
