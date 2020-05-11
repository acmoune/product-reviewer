name := "product-reviewer"

lazy val commonSettings = Seq(
  organization := "com.epoqee",
  version := "1.0.0",
  scalaVersion := "2.12.8")

lazy val streamAppSettings = Seq(
  resolvers ++= Seq("Confluent" at "https://packages.confluent.io/maven"),
  libraryDependencies ++= Seq(
    "org.apache.kafka" %% "kafka-streams-scala" % kafkaClientsVerion,
    "org.apache.avro" % "avro" % avroVersion,
    "io.confluent" % "kafka-avro-serializer" % confluentVersion,
    "io.confluent" % "kafka-streams-avro-serde" % confluentVersion,
    "io.confluent" % "kafka-schema-registry-client" % confluentVersion,
    "ch.qos.logback" % "logback-classic" % logbackVersion,
    "com.typesafe" % "config" % typesafeConfigVersion))

lazy val root = (project in file(".")).aggregate(`common`, `webserver`, `security`, `reviews`, `statistics`)

lazy val common = (project in file("common"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "commons-daemon" % "commons-daemon" % commonDaemonVersion),
    AvroConfig / sourceDirectory := file("common/src/main/avro"),
    AvroConfig / javaSource := file("common/src/main/java/avro"),
    AvroConfig / stringType := "String")

lazy val `webserver` = (project in file("webserver"))
  .dependsOn(common)
  .enablePlugins(PlayScala)
  .settings(commonSettings: _*)
  .settings(
    resolvers ++= Seq("Confluent" at "https://packages.confluent.io/maven"),
    libraryDependencies ++= Seq(
      guice,
      "org.apache.avro" % "avro" % avroVersion,
      "io.confluent" % "kafka-avro-serializer" % confluentVersion,
      "org.apache.kafka" % "kafka-clients" % kafkaClientsVerion,
      "com.datastax.oss" % "java-driver-core" % cassandraDriverVersion,
      "com.datastax.oss" % "java-driver-query-builder" % cassandraDriverVersion,
      "com.typesafe.akka" %% "akka-stream-kafka" % alpakkaKafkaConnectorVersion))

lazy val `security` = (project in file("security"))
  .dependsOn(common)
  .enablePlugins(JavaAppPackaging)
  .settings(commonSettings: _*)
  .settings(streamAppSettings: _*)

lazy val `reviews` = (project in file("reviews"))
  .dependsOn(common)
  .enablePlugins(JavaAppPackaging)
  .settings(commonSettings: _*)
  .settings(streamAppSettings: _*)

lazy val `statistics` = (project in file("statistics"))
  .dependsOn(common)
  .enablePlugins(JavaAppPackaging)
  .settings(commonSettings: _*)
  .settings(streamAppSettings: _*)

val runAll = inputKey[Unit]("Runs all services")

runAll := {
  (run in Compile in `security`).evaluated
  (run in Compile in `reviews`).evaluated
  (run in Compile in `statistics`).evaluated
  (run in Compile in `webserver`).partialInput(" 9000").evaluated
}

fork in run := true

val confluentVersion = "5.4.1"
val kafkaClientsVerion = "2.4.0"
val avroVersion = "1.9.1"
val logbackVersion = "1.2.3"
val typesafeConfigVersion = "1.4.0"
val commonDaemonVersion = "1.2.2"
val cassandraDriverVersion = "4.5.0"
val alpakkaKafkaConnectorVersion = "2.0.2"