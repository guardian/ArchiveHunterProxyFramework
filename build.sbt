val awsSdkVersion = "1.11.346"
val circeVersion = "0.9.3"
val specs2Version = "4.3.2"

enablePlugins(RiffRaffArtifact)

lazy val root = (project in file("."))
  .settings(
    name := "ArchiveHunterProxyLambdas"
  ).aggregate(requestLambda, ecsAlertLambda, transcoderReplyLambda)

lazy val `requestLambda` = (project in file("ProxyRequestLambda"))
  .settings(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-lambda" % awsSdkVersion,
      "com.amazonaws" % "aws-lambda-java-events" % "2.1.0",
      "com.amazonaws" % "aws-lambda-java-core" % "1.0.0",
      "com.amazonaws" % "aws-java-sdk-sns"% awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-ecs" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-elastictranscoder" % awsSdkVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-java8" % circeVersion,
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
  .settings(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-lambda" % awsSdkVersion,
      "com.amazonaws" % "aws-lambda-java-events" % "2.1.0",
      "com.amazonaws" % "aws-lambda-java-core" % "1.0.0",
      "com.amazonaws" % "aws-java-sdk-elastictranscoder" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-sns" % awsSdkVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-java8" % circeVersion,
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

lazy val `ecsAlertLambda` = (project in file("ECSAlertLambda"))
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
      "io.circe" %% "circe-java8" % circeVersion,
      "org.slf4j" % "slf4j-api" % "1.7.25",
      "com.amazonaws" % "aws-lambda-java-log4j2" % "1.0.0",
      "org.specs2" %% "specs2-core" % specs2Version % "test",
      "org.specs2" %% "specs2-mock" % specs2Version % "test"
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
  (assembly  in ecsAlertLambda).value -> s"archivehunter-proxyecsalert-lambda/${(assembly in ecsAlertLambda).value.getName}",
  (assembly in transcoderReplyLambda).value -> s"archivehunter-transcoderreply-lambda/${(assembly in transcoderReplyLambda).value.getName}",
  (baseDirectory in Global in root).value / "riff-raff.yaml" -> "riff-raff.yaml",
)

