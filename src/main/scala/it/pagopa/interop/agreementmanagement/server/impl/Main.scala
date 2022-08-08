package it.pagopa.interop.agreementmanagement.server.impl

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem}
import akka.cluster.ClusterEvent
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.typed.{Cluster, Subscribe}
import akka.http.scaladsl.Http
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import cats.syntax.all._
import it.pagopa.interop.agreementmanagement.common.system.ApplicationConfiguration
import it.pagopa.interop.agreementmanagement.server.Controller
import it.pagopa.interop.commons.logging.renderBuildInfo
import scala.concurrent.ExecutionContextExecutor
import akka.actor.typed.ActorRef
import scala.util.{Success, Failure}
import buildinfo.BuildInfo

import com.typesafe.scalalogging.Logger
import scala.concurrent.Future
import akka.actor.typed.DispatcherSelector

object Main extends App with Dependencies {

  val logger: Logger = Logger(this.getClass())

  ActorSystem[Nothing](
    Behaviors.setup[Nothing] { context =>
      implicit val actorSystem: ActorSystem[Nothing]          = context.system
      implicit val executionContext: ExecutionContextExecutor = actorSystem.executionContext
      val selector: DispatcherSelector                        = DispatcherSelector.fromConfig("futures-dispatcher")
      val blockingEc: ExecutionContextExecutor                = actorSystem.dispatchers.lookup(selector)

      AkkaManagement.get(actorSystem.classicSystem).start()

      val sharding: ClusterSharding = ClusterSharding(actorSystem)
      sharding.init(agreementPersistenceEntity)

      val cluster: Cluster = Cluster(actorSystem)
      ClusterBootstrap.get(actorSystem.classicSystem).start()

      val listener: ActorRef[ClusterEvent.MemberEvent] = context.spawn(
        Behaviors.receive[ClusterEvent.MemberEvent]((ctx, event) => {
          ctx.log.debug("MemberEvent: {}", event)
          Behaviors.same
        }),
        "listener"
      )
      cluster.subscriptions ! Subscribe(listener, classOf[ClusterEvent.MemberEvent])

      if (ApplicationConfiguration.projectionsEnabled) initProjections(blockingEc)

      logger.info(renderBuildInfo(BuildInfo))
      logger.info(s"Started cluster at ${cluster.selfMember.address}")

      val serverBinding: Future[Http.ServerBinding] = for {
        jwtReader <- getJwtValidator()
        api        = agreementApi(sharding, jwtReader)
        controller = new Controller(api, validationExceptionToRoute.some)(actorSystem.classicSystem)
        binding <- Http().newServerAt("0.0.0.0", ApplicationConfiguration.serverPort).bind(controller.routes)
      } yield binding

      serverBinding.onComplete {
        case Success(b) =>
          logger.info(s"Started server at ${b.localAddress.getHostString()}:${b.localAddress.getPort()}")
        case Failure(e) =>
          actorSystem.terminate()
          logger.error("Startup error: ", e)
      }

      Behaviors.empty
    },
    BuildInfo.name
  )

}
