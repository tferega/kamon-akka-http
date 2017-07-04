/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package playground

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import kamon.Kamon
import kamon.jaeger.Jaeger
import kamon.prometheus.PrometheusReporter
import org.slf4j.LoggerFactory

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.io.StdIn

object WebServer extends App {
  val logger = LoggerFactory.getLogger(this.getClass)
  logger.info("KamonAkkaHttp Playground Server now booting up")

  logger.debug("Loading config")
  val config = ConfigFactory.load()
  logger.trace(config.root().render(ConfigRenderOptions.concise()))

  logger.debug("Registering Kamon reporters")
  Kamon.addReporter(new PrometheusReporter)
  Kamon.addReporter(new Jaeger)

  logger.debug("Starting the HTTP server")
  implicit val system = ActorSystem()
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()
  try {
    val host = config.getString("playground.host")
    val port = config.getInt("playground.port")
     val bindingFuture = Http().bindAndHandle(buildRoutes, host, port)
      .flatMap { serverBinding ⇒
        logger.info(s"Server online at http://$host:$port")
        logger.info("Press RETURN to stop...")
        StdIn.readLine()

        logger.info(s"Server is shutting down.")
        serverBinding.unbind
        Future(())
      }
    Await.result(bindingFuture, Duration.Inf)
  } catch {
    case t: Throwable => logger.error("AN ERROR OCCURRED!", t)
  } finally {
    val fShutdown = for {
      _ <- system.terminate
      _ <- Kamon.stopAllReporters
    } yield ()
    Await.result(fShutdown, Duration.Inf)
    logger.info("HALTING")
    sys.exit(0)
  }

  def buildRoutes: Route = {
    get {
      path("ok") {
        Kamon.counter("ok").increment
        logger.info("Completing /ok with code 200")
        complete("ok")
      } ~
      path("query-internal") {
        val internalHost = config.getString("playground.host")
        val internalPort = config.getInt("playground.port")
        val result = httpGet(s"http://$internalHost:$internalPort/ok")
        result.foreach { e => logger.info(s"Completing /query-internal with payload size of ${e.entity.contentLengthOption}") }
        complete(result)
      } ~
      path("go-to-outside") {
        Kamon.counter("go-to-outside").increment
        val serviceHost = config.getString("playground.services.ip-api.host")
        val servicePort = config.getInt("playground.services.ip-api.port")
        val result = httpGet(s"http://$serviceHost:$servicePort/")
        result.foreach { e => logger.info(s"Completing /go-to-outside with payload size of ${e.entity.contentLengthOption}") }
        complete(result)
      } ~
      path("internal-error") {
        Kamon.counter("internal-error").increment
        logger.info("Completing /internal-error with code 500")
        complete(HttpResponse(StatusCodes.InternalServerError))
      } ~
      path("fail-with-exception") {
        Kamon.counter("fail-with-exception").increment
        logger.info("Failing /fail-with-exception with a RuntimeException")
        throw new RuntimeException("Failing on purpose!")
      }
    }
  }

  def httpGet(url: String): Future[HttpResponse] = {
    val request = HttpRequest(uri = url)
    Http().singleRequest(request)
  }
}

