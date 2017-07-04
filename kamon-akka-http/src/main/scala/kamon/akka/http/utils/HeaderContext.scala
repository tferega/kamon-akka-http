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
package utils

import java.util
import java.util.Map

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import io.opentracing.SpanContext
import io.opentracing.propagation.Format.Builtin
import io.opentracing.propagation.Format.Builtin.HTTP_HEADERS
import io.opentracing.propagation.TextMap
import kamon.Kamon
import kamon.trace.{SpanContext => KamonSpanContext}
import kamon.util.HexCodec

import scala.collection.JavaConverters._
import scala.collection.mutable

object HeaderContext {
  private val includeHeaders = AkkaHttpExtension.settings.includeTraceTokenHeader

  def extractRequest(request: HttpRequest): Option[SpanContext] = {
    if (includeHeaders) {
      val textMap = readOnlyTextMapFromHeaders(request.headers)
      Option(Kamon.extract(HTTP_HEADERS, textMap))
    } else None
  }

  def injectClientHeaders(context: SpanContext, headers: List[HttpHeader]): List[HttpHeader] = {
    if (includeHeaders) {
      val injectedMap = mutable.Map.empty[String, String]
      Kamon.inject(context, Builtin.HTTP_HEADERS, textMapFromRequest(injectedMap))
      injectedMap.foldRight(headers)((p, r) => r :+ RawHeader(p._1, p._2))
    } else headers
  }

  def injectResponse(response: HttpResponse, context: SpanContext): HttpResponse = {
    if (includeHeaders) {
      context match {
        case c: KamonSpanContext => response.addHeader(RawHeader("X-B3-SpanId", encodeLong(c.spanID)))
        case _ => response
      }
    } else response
  }

  private def textMapFromRequest(map: mutable.Map[String, String]): TextMap = new TextMap {
    override def put(key: String, value: String): Unit = map.put(key, value)
    override def iterator(): util.Iterator[java.util.Map.Entry[String, String]] = throw new RuntimeException("Intentionally not implemented")
  }

  def readOnlyTextMapFromHeaders(headers: Seq[HttpHeader]): TextMap = new TextMap {
    override def put(key: String, value: String): Unit = {}

    override def iterator(): util.Iterator[Map.Entry[String, String]] =
      headers.map(h => new Map.Entry[String, String] {
        override def getKey: String = h.name()
        override def getValue: String = h.value()
        override def setValue(value: String): String = value
      }).iterator.asJava
  }

  private def encodeLong(input: Long): String = HexCodec.toLowerHex(input)
}
