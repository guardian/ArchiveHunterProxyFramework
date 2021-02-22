val awsSdkVersion = "1.11.959"
val circeVersion = "0.13.0"
val specs2Version = "4.3.2"
val jacksonDatabindVersion = "2.9.10.8"

enablePlugins(RiffRaffArtifact)

lazy val root = (project in file("."))
  .settings(
    name := "ArchiveHunterProxyLambdas"
  ).aggregate(requestLambda, ecsAlertLambda, transcoderReplyLambda, sweeperLambda)

lazy val common = (project in file("common"))
  .settings(
    name := "Common",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
    )
  )

lazy val `requestLambda` = (project in file("ProxyRequestLambda"))
  .dependsOn(common)
  .settings(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-lambda" % awsSdkVersion,
      "com.amazonaws" % "aws-lambda-java-events" % "2.1.0",
      "com.amazonaws" % "aws-lambda-java-core" % "1.0.0",
      "com.amazonaws" % "aws-java-sdk-sns"% awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-ecs" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-elastictranscoder" % awsSdkVersion,
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonDatabindVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "org.slf4j" % "slf4j-api" % "1.7.25",
      "com.amazonaws" % "aws-lambda-java-log4j2" % "1.0.0",
      "org.specs2" %% "specs2-core" % specs2Version % "test",
      "org.specs2" %% "specs2-mock" % specs2Version % "test"
    ),
    assemblyJarName in assembly := "proxyRequestLambda.jar",
    assemblyMergeStrategy in assembly := {
      case PathList("javax", "servlet", xs @ _*)         => MergeStrategy.first
      case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
      case "application.conf" => MergeStrategy.concat
      //META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat
      case PathList("META-INF","org","apache","logging","log4j","core","config","plugins","Log4j2Plugins.dat") => MergeStrategy.last
      case x=>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  )

lazy val `transcoderReplyLambda` = (project in file("TranscoderReplyLambda"))
  .dependsOn(common)
  .settings(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-lambda" % awsSdkVersion,
      "com.amazonaws" % "aws-lambda-java-events" % "2.1.0",
      "com.amazonaws" % "aws-lambda-java-core" % "1.0.0",
      "com.amazonaws" % "aws-java-sdk-elastictranscoder" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-sns" % awsSdkVersion,
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonDatabindVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "org.slf4j" % "slf4j-api" % "1.7.25",
      "com.amazonaws" % "aws-lambda-java-log4j2" % "1.0.0",
      "org.specs2" %% "specs2-core" % specs2Version % "test",
      "org.specs2" %% "specs2-mock" % specs2Version % "test"
    ),
    assemblyJarName in assembly := "transcoderReplyLambda.jar",
    assemblyMergeStrategy in assembly := {
      case PathList("javax", "servlet", xs @ _*)         => MergeStrategy.first
      case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
      case "application.conf" => MergeStrategy.concat
      //META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat
      case PathList("META-INF","org","apache","logging","log4j","core","config","plugins","Log4j2Plugins.dat") => MergeStrategy.last
      case x=>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  )

lazy val `sweeperLambda` = (project in file("SweeperLambda"))
  .dependsOn(common)
  .settings(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-lambda" % awsSdkVersion,
      "com.amazonaws" % "aws-lambda-java-events" % "2.1.0",
      "com.amazonaws" % "aws-lambda-java-core" % "1.0.0",
      "com.amazonaws" % "aws-java-sdk-sns" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-sqs" % awsSdkVersion,
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonDatabindVersion,
      "org.slf4j" % "slf4j-api" % "1.7.25",
      "com.amazonaws" % "aws-lambda-java-log4j2" % "1.0.0",
      "org.specs2" %% "specs2-core" % specs2Version % "test",
      "org.specs2" %% "specs2-mock" % specs2Version % "test"
    ),
    assemblyJarName in assembly := "sweeperLambda.jar",
    assemblyMergeStrategy in assembly := {
      case PathList("javax", "servlet", xs @ _*)         => MergeStrategy.first
      case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
      case "application.conf" => MergeStrategy.concat
      //META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat
      case PathList("META-INF","org","apache","logging","log4j","core","config","plugins","Log4j2Plugins.dat") => MergeStrategy.last
      case x=>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  )
lazy val `ecsAlertLambda` = (project in file("ECSAlertLambda"))
  .dependsOn(common)
  .settings(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-lambda" % awsSdkVersion,
      "com.amazonaws" % "aws-lambda-java-events" % "2.1.0",
      "com.amazonaws" % "aws-lambda-java-core" % "1.0.0",
      "com.amazonaws" % "aws-java-sdk-sqs"% awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-ecs" % awsSdkVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "org.slf4j" % "slf4j-api" % "1.7.25",
      "com.amazonaws" % "aws-lambda-java-log4j2" % "1.1.1",
      "org.specs2" %% "specs2-core" % specs2Version % "test",
      "org.specs2" %% "specs2-mock" % specs2Version % "test",
      //fixes for vulnerable dependencies
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonDatabindVersion,
      "commons-codec" % "commons-codec" % "1.13",
    ),
    assemblyJarName in assembly := "ecsAlertLambda.jar",
    assemblyMergeStrategy in assembly := {
      case PathList("javax", "servlet", xs @ _*)         => MergeStrategy.first
      case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
      case "application.conf" => MergeStrategy.concat
      //META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat
      case PathList("META-INF","org","apache","logging","log4j","core","config","plugins","Log4j2Plugins.dat") => MergeStrategy.last
      case x=>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)

    }
  )

val jsTargetDir = "target/riffraff/packages"

riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")
riffRaffManifestProjectName := "multimedia:ArchiveHunterProxyFramework"
riffRaffArtifactResources := Seq(
  (assembly in requestLambda).value -> s"archivehunter-proxyrequest-lambda/${(assembly  in requestLambda).value.getName}",
  (assembly in sweeperLambda).value -> s"archivehunter-sweeper-lambda/${(assembly in sweeperLambda).value.getName}",
  (assembly in ecsAlertLambda).value -> s"archivehunter-proxyecsalert-lambda/${(assembly in ecsAlertLambda).value.getName}",
  (assembly in transcoderReplyLambda).value -> s"archivehunter-transcoderreply-lambda/${(assembly in transcoderReplyLambda).value.getName}",
  (baseDirectory in Global in root).value / "riff-raff.yaml" -> "riff-raff.yaml",
)

