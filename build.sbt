import Dependencies._

ThisBuild / scalaVersion     := "2.12.8"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

val awsSdkVersion = "1.11.346"
val circeVersion = "0.9.3"

lazy val root = (project in file("."))
  .settings(
    name := "ArchiveHunterProxyLambdas",
    libraryDependencies += scalaTest % Test
  )

lazy val requestLambda = (project in file("ProxyRequestLambda"))
  .settings(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-lambda" % awsSdkVersion,
      "com.amazonaws" % "aws-lambda-java-events" % "2.1.0",
      "com.amazonaws" % "aws-lambda-java-core" % "1.0.0",
      "com.amazonaws" % "aws-java-sdk-sqs"% awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-ecs" % awsSdkVersion,
      "com.google.inject" % "guice" % "4.1.0",  //keep this in sync with play version
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-java8" % circeVersion,
      "com.amazonaws" % "aws-lambda-java-log4j2" % "1.0.0",
    )
  )