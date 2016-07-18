name := "salesforce-webhook-creator"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  ws,
  filters,
  "org.webjars" %% "webjars-play" % "2.5.0-2",
  "org.webjars" % "requirejs" % "2.1.11-1",
  "org.webjars" % "angular-ui-bootstrap" % "1.3.3",
  specs2 % Test
)

pipelineStages := Seq(digest, gzip)
