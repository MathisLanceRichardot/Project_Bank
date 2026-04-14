ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.7"

lazy val akkaVersion   = "2.8.8"
lazy val scalaFxVersion = "26.0.0-R38"

lazy val root = (project in file("."))
  .settings(
    name := "Project_Bank",

    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed"         % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j"               % akkaVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
      "org.scalatest"     %% "scalatest"                % "3.2.20"    % Test,
      "ch.qos.logback"     % "logback-classic"          % "1.5.32",
      "org.scalafx"       %% "scalafx"                  % scalaFxVersion
    ),

    // Nécessaire pour JavaFX sur Java 11+
    javaOptions ++= Seq(
      "--add-modules", "javafx.controls,javafx.fxml"
    ),
    fork := true
  )