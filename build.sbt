name := "TinyURL"

version := "0.1"

scalaVersion := "2.12.4"
//akka
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % "10.0.10",
  // Only when running against Akka 2.5 explicitly depend on akka-streams in same version as akka-actor
  "com.typesafe.akka" %% "akka-stream" % "2.5.6", // or whatever the latest version is
  "com.typesafe.akka" %% "akka-actor" % "2.5.6")
//redis
resolvers += Resolver.bintrayRepo("jastice","maven")
libraryDependencies += "com.github.scredis" %% "scredis" % "2.1.1"
//mongodb
libraryDependencies += "org.mongodb" %% "casbah" % "3.1.1"
//scalatest
//libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.4"
//libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.4" % "test"
//junit
//libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test"

//log
libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.25" % "test"

//command line
libraryDependencies += "com.github.scopt" %% "scopt" % "3.7.0"