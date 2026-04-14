import com.typesafe.sbt.packager.Keys.*

ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.7"

lazy val root = project.in(file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "Project_Bank",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % "2.8.8",
      "org.scalafx"       %% "scalafx"          % "23.0.1-R34",
      "ch.qos.logback"     % "logback-classic"  % "1.5.32"
    ),

    Compile / mainClass := Some("Bank.ui."),

    Universal / javaOptions ++= Seq(
      "-Dfile.encoding=UTF-8",
      "--add-modules", "javafx.controls,javafx.fxml",
      "--add-exports", "javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
      "--enable-native-access=ALL-UNNAMED"
    ),

    fork := true
  )