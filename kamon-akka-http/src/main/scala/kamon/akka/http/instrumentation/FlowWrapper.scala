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
package instrumentation

import akka.NotUsed
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream._
import akka.stream.scaladsl.{BidiFlow, Flow}
import akka.stream.stage._
import kamon.Kamon
import kamon.akka.http.utils.HeaderContext
import org.slf4j.LoggerFactory

/**
 * Wraps an {@code Flow[HttpRequest,HttpResponse]} with the necessary steps to output
 * the http metrics defined in AkkaHttpServerMetrics.
 * credits to @jypma.
 */
object FlowWrapper {
  private val metrics = AkkaHttpExtension.metrics

  def apply(flow: Flow[HttpRequest, HttpResponse, NotUsed]): Flow[HttpRequest, HttpResponse, NotUsed] = BidiFlow.fromGraph(wrap).join(flow)

  private def wrap = new GraphStage[BidiShape[HttpRequest, HttpRequest, HttpResponse, HttpResponse]] {
    private val requestIn = Inlet.create[HttpRequest]("request.in")
    private val requestOut = Outlet.create[HttpRequest]("request.out")
    private val responseIn = Inlet.create[HttpResponse]("response.in")
    private val responseOut = Outlet.create[HttpResponse]("response.out")

    override val shape = BidiShape(requestIn, requestOut, responseIn, responseOut)

    override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {
      setHandler(requestIn, new InHandler {
        override def onPush(): Unit = {
          val request = grab(requestIn)
          val defaultSpanName = AkkaHttpExtension.generateSpanName(request)
          val context = HeaderContext.extractRequest(request)
          Kamon.buildSpan(defaultSpanName).asChildOf(context.orNull).startActive
          metrics.activeRequests.increment
          push(requestOut, request)
        }
        override def onUpstreamFinish(): Unit = complete(requestOut)
      })

      setHandler(requestOut, new OutHandler {
        override def onPull(): Unit = pull(requestIn)
        override def onDownstreamFinish(): Unit = cancel(requestIn)
      })

      setHandler(responseIn, new InHandler {
        override def onPush(): Unit = {
          val response = Kamon.fromActiveSpan { span =>
            span.deactivate()
            val response = grab(responseIn)
            metrics.activeRequests.decrement
            HeaderContext.injectResponse(response, span.context)
          } getOrElse grab(responseIn)
          push(responseOut, response)
        }
        override def onUpstreamFinish(): Unit = completeStage()
      })

      setHandler(responseOut, new OutHandler {
        override def onPull(): Unit = pull(responseIn)
        override def onDownstreamFinish(): Unit = cancel(responseIn)
      })

      override def preStart(): Unit = metrics.openConnections.increment
      override def postStop(): Unit = metrics.openConnections.decrement
    }
  }
}

