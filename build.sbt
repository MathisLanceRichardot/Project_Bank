import sbtassembly.AssemblyPlugin.autoImport._

ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.7"

lazy val root = project.in(file("."))
  .settings(
    name := "Project_Bank",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % "2.8.8",
      "org.scalafx"       %% "scalafx"          % "23.0.1-R34",
      "ch.qos.logback"     % "logback-classic"  % "1.5.32"
    ),

    // Configuration pour le JAR unique (Assembly)
    assembly / mainClass := Some("Bank.ui.BankUI"),

    assembly / assemblyMergeStrategy := {
      case PathList("reference.conf") => MergeStrategy.concat
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case x => MergeStrategy.first
    },

    // Options pour JavaFX et Akka
    run / javaOptions ++= Seq(
      "-Dfile.encoding=UTF-8",
      "--add-modules", "javafx.controls,javafx.fxml"
    ),

    fork := true
  )