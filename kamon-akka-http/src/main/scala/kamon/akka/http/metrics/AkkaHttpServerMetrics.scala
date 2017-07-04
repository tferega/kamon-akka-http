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
package metrics

import kamon.Kamon
import kamon.metric._

object AkkaHttpServerMetrics {
  /**
    *
    * Metrics for Akka-Http Server:
    *
    *    - active-requests: Number of active requests
    *    - open-connections: Number of open connections
    */
  private val activeRequestsMetric = Kamon.minMaxCounter("akka-http-server.requests-active")
  private val openConnectionsMetric = Kamon.minMaxCounter("akka-http-server.connections-open")

  def forHttpServer(path: String): AkkaHttpServerMetrics = {
    val tags = Map("path" -> path)
    AkkaHttpServerMetrics(
      tags,
      activeRequestsMetric.refine(tags),
      openConnectionsMetric.refine(tags)
    )
  }
}

case class AkkaHttpServerMetrics(tags: Map[String, String], activeRequests: MinMaxCounter, openConnections: MinMaxCounter) {
  import AkkaHttpServerMetrics._
  def cleanup(): Unit = {
    activeRequestsMetric.remove(tags)
    openConnectionsMetric.remove(tags)
  }
}
