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

val kamonAkka = "io.kamon" %% "kamon-akka-2.4" % "1.0.0-RC1-5472bca942c01bb87720263b36978cc0b243365e"
val kamonCore = "io.kamon" %% "kamon-core"     % "1.0.0-RC1-640e7098f42c3f497c493244b6652c9408fae9a8"

val http        = "com.typesafe.akka" %% "akka-http"         % "10.0.9"
val httpTestKit = "com.typesafe.akka" %% "akka-http-testkit" % "10.0.9"

lazy val root = (project in file("."))
  .aggregate(kamonAkkaHttp, kamonAkkaHttpPlayground)
  .settings(noPublishing: _*)
  .settings(Seq(crossScalaVersions := Seq("2.11.8", "2.12.1")))

lazy val kamonAkkaHttp = Project("kamon-akka-http", file("kamon-akka-http"))
  .settings(resolvers += Resolver.bintrayRepo("kamon-io", "snapshots"))
  .settings(name := "kamon-akka-http")
  .settings(aspectJSettings: _*)
  .settings(Seq(
    scalaVersion := "2.12.1",
    crossScalaVersions := Seq("2.11.8", "2.12.1"),
    testGrouping in Test := singleTestPerJvm((definedTests in Test).value, (javaOptions in Test).value)))
  .settings(libraryDependencies ++=
    compileScope(http, kamonCore, kamonAkka) ++
      testScope(httpTestKit, scalatest, slf4jApi, slf4jnop) ++
      providedScope(aspectJ))

lazy val kamonAkkaHttpPlayground = Project("kamon-akka-http-playground", file("kamon-akka-http-playground"))
  .dependsOn(kamonAkkaHttp)
  .settings(Seq(
    scalaVersion := "2.12.1",
    crossScalaVersions := Seq("2.11.8", "2.12.1")))
  .settings(noPublishing: _*)
  .settings(settingsForPlayground: _*)
  .settings(libraryDependencies ++=
    compileScope(http) ++
    testScope(httpTestKit, scalatest, slf4jApi, slf4jnop) ++
    providedScope(aspectJ))

lazy val settingsForPlayground: Seq[Setting[_]] = Seq(
  connectInput in run := true,
  cancelable in Global := true
)

import sbt.Tests._
def singleTestPerJvm(tests: Seq[TestDefinition], jvmSettings: Seq[String]): Seq[Group] =
  tests map { test =>
    Group(
      name = test.name,
      tests = Seq(test),
      runPolicy = SubProcess(ForkOptions(runJVMOptions = jvmSettings)))
  }
