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

package kamon.akka.http

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.Host
import kamon.Kamon
import kamon.akka.http.metrics.AkkaHttpServerMetrics

object AkkaHttpExtension extends Logging {
  logger.info("Starting the Kamon(Akka-Http) extension")

  val settings = AkkaHttpExtensionSettings(Kamon.config)
  val ChildLibraryName = "akka-http-client"
  val metrics: AkkaHttpServerMetrics = AkkaHttpServerMetrics.forHttpServer("akka-http-server")

  def generateSpanName(request: HttpRequest): String =
    settings.nameGenerator.generateSpanName(request)

  def generateRequestLevelApiChildName(request: HttpRequest): String =
    settings.nameGenerator.generateRequestLevelApiChildName(request)

  def generateHostLevelApiChildName(request: HttpRequest): String =
    settings.nameGenerator.generateHostLevelApiChildName(request)
}

trait NameGenerator {
  def generateSpanName(request: HttpRequest): String
  def generateRequestLevelApiChildName(request: HttpRequest): String
  def generateHostLevelApiChildName(request: HttpRequest): String
}

class DefaultNameGenerator extends NameGenerator {

  def generateRequestLevelApiChildName(request: HttpRequest): String = {
    val uriAddress = request.uri.authority.host.address
    if (uriAddress.equals("")) hostFromHeaders(request).getOrElse("unknown-host") else uriAddress
  }

  def generateHostLevelApiChildName(request: HttpRequest): String =
    hostFromHeaders(request).getOrElse("unknown-host")

  def generateSpanName(request: HttpRequest): String =
    "UnnamedSpan"

  private def hostFromHeaders(request: HttpRequest): Option[String] =
    request.header[Host].map(_.host.toString())
}
