name := "salesforce-webhook-creator"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  ws,
  filters,
  "com.force.api" % "force-apex-api" % "33.0.1",
  "com.force.api" % "force-metadata-api" % "33.0.1",
  "com.force.api" % "force-partner-api" % "33.0.3",
  "org.webjars" %% "webjars-play" % "2.3.0",
  "org.webjars" % "requirejs" % "2.1.11-1",
  "org.webjars" % "angular-ui-bootstrap" % "0.11.0-2",
  "org.webjars" % "angularjs" % "1.2.17"
)

pipelineStages := Seq(digest, gzip)