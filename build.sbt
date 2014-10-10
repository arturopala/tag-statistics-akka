organization := "code.arturopala"

name := "tag-statistics-akka"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.11.2"

resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.+" withSources(),
  "com.typesafe.akka" %% "akka-testkit" % "2.3.+" withSources(),
  "org.scalatest" %% "scalatest" % "2.+" % "test",
  "junit" % "junit" % "4.+" % "test",
  "com.novocode" % "junit-interface" % "0.10" % "test"
)

