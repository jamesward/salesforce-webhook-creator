enablePlugins(PlayScala)

name := "salesforce-webhook-creator"

scalaVersion := "2.13.8"

libraryDependencies ++= Seq(
  ws,
  guice,
  filters,
  "org.webjars" %% "webjars-play" % "2.8.0-1",
  "org.webjars" % "requirejs" % "2.1.11-1",
  "org.webjars" % "angular-ui-bootstrap" % "1.3.3",
  specs2 % Test
)

pipelineStages := Seq(digest, gzip)
