package it.pagopa.interop.agreementmanagement.common.system

import com.typesafe.config.{Config, ConfigFactory}

object ApplicationConfiguration {
  System.setProperty("kanela.show-banner", "false")
  val config: Config = ConfigFactory.load()

  val serverPort: Int          = config.getInt("agreement-management.port")
  val jwtAudience: Set[String] =
    config.getString("agreement-management.jwt.audience").split(",").toSet.filter(_.nonEmpty)
  val queueUrl: String         = config.getString("agreement-management.persistence-events-queue-url")

  val numberOfProjectionTags: Int = config.getInt("akka.cluster.sharding.number-of-shards")
  def projectionTag(index: Int)   = s"interop-be-agreement-management-persistence|$index"
  val projectionsEnabled: Boolean = config.getBoolean("akka.projection.enabled")

  val mongoDb: MongoDbConfig = {
    val connectionString: String = config.getString("agreement-management.cqrs-projection.db.connection-string")
    val dbName: String           = config.getString("agreement-management.cqrs-projection.db.name")
    val collectionName: String   = config.getString("agreement-management.cqrs-projection.db.collection-name")

    MongoDbConfig(connectionString, dbName, collectionName)
  }

  require(jwtAudience.nonEmpty, "Audience cannot be empty")
}
