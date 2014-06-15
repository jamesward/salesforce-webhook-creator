name := "salesforce-webhooks"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  ws,
  "org.webjars" % "webjars-locator" % "0.16",
  "org.webjars" % "requirejs" % "2.1.11-1",
  "com.force.api" % "force-apex-api" % "29.0.0",
  "com.force.api" % "force-metadata-api" % "29.0.0",
  "com.force.api" % "force-partner-api" % "29.0.0",
  "org.webjars" % "angular-ui-bootstrap" % "0.11.0-2",
  "org.webjars" % "angularjs" % "1.2.17"
)

pipelineStages := Seq(rjs, digest, gzip)