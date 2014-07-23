name := "tag-statistics-akka"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.+",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.+",
  "org.scalatest" %% "scalatest" % "2.+" % "test",
  "junit" % "junit" % "4.+" % "test",
  "com.novocode" % "junit-interface" % "0.10" % "test"
)

