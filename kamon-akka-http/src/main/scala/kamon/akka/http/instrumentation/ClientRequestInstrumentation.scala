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

import akka.http.scaladsl.model._
import io.opentracing.{NoopSpan, Span}
import kamon.Kamon
import kamon.akka.http.utils.{HeaderContext, SameThreadExecutionContext}
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

import scala.concurrent.Future

object ClientRequestInstrumentation {
  private implicit val ec = SameThreadExecutionContext
}

@Aspect
class ClientRequestInstrumentation {
  import ClientRequestInstrumentation._

  @Around("execution(* akka.http.scaladsl.HttpExt.singleRequest(..)) && args(request, *, *, *, *)")
  def onSingleRequest(pjp: ProceedingJoinPoint, request: HttpRequest): Any = {
    val child: Span =
      if (AkkaHttpExtension.settings.clientInstrumentationLevel == ClientInstrumentationLevel.RequestLevelAPI) {
        val childName = AkkaHttpExtension.generateRequestLevelApiChildName(request)
        Kamon.buildSpan(childName)
          .withTag("category", "http-client")
          .withTag("library-name", AkkaHttpExtension.ChildLibraryName)
          .startManual
      } else NoopSpan.INSTANCE

    val responseFuture = pjp.proceed.asInstanceOf[Future[HttpResponse]]
    responseFuture.onComplete { result =>
      child.setTag("error", result.isFailure)
      child.finish()
    }
    responseFuture
  }

  @Around("execution(* akka.http.scaladsl.model.HttpMessage.withDefaultHeaders(*)) && this(request) && args(defaultHeaders)")
  def onWithDefaultHeaders(pjp: ProceedingJoinPoint, request: HttpMessage, defaultHeaders: List[HttpHeader]): Any = {
    val modifiedHeaders = Kamon.fromActiveSpan { span =>
      HeaderContext.injectClientHeaders(span.context, defaultHeaders)
    } getOrElse defaultHeaders
    pjp.proceed(Array[AnyRef](request, modifiedHeaders))
  }
}
