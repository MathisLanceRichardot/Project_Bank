ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.18"


lazy val akkaVersion  = "2.8.8"

lazy val root = (project in file("."))
  .settings(
    name := "Project_Bank",

    libraryDependencies ++= Seq(

      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion
    ),
  )