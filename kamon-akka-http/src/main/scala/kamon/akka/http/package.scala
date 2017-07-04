package kamon.akka

import org.slf4j.{Logger, LoggerFactory}

package object http {
  trait Logging {
    protected val logger: Logger = LoggerFactory.getLogger(this.getClass)
  }
}
