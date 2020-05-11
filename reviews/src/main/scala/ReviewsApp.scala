import java.util.Properties
import collection.JavaConverters._
import common.daemon._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig, Topology}
import org.slf4j.{Logger, LoggerFactory}
import com.typesafe.config.ConfigFactory
import common.avro.Task
import common.avro.reviews.entities.{Review, ReviewStatus}
import common.avro.reviews.events.{ReviewApproved, ReviewRejected}
import common.avro.security.events.SecurityCheckProcessed
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.streams.scala.kstream.{Consumed, Joined, Produced}
import org.apache.kafka.streams.scala.Serdes._

class ReviewsApp extends ApplicationLifecycle {
  private val LOG: Logger = LoggerFactory.getLogger("reviews-app")
  private[this] var started = false
  private val applicationName = "Reviews"
  private val config = ConfigFactory.load()

  private val serdeConfig = Map[String, Any](
    "schema.registry.url"-> config.getString("kafka.schema.registry.url"),
    "specific.avro.reader"-> true,
    "value.subject.name.strategy"-> classOf[TopicRecordNameStrategy].getName)

  private implicit val specificSerdes: SpecificAvroSerde[SpecificRecord] = new SpecificAvroSerde[SpecificRecord]()
  specificSerdes.configure(serdeConfig.asJava, false)

  private implicit val rewRejSerdes: SpecificAvroSerde[ReviewRejected] = new SpecificAvroSerde[ReviewRejected]()
  rewRejSerdes.configure(serdeConfig.asJava, false)

  private implicit val rewAppSerdes: SpecificAvroSerde[ReviewApproved] = new SpecificAvroSerde[ReviewApproved]()
  rewAppSerdes.configure(serdeConfig.asJava, false)

  private implicit val secCheckProcSerdes: SpecificAvroSerde[SecurityCheckProcessed] = new SpecificAvroSerde[SecurityCheckProcessed]()
  secCheckProcSerdes.configure(serdeConfig.asJava, false)

  private implicit val revSerdes: SpecificAvroSerde[Review] = new SpecificAvroSerde[Review]()
  revSerdes.configure(serdeConfig.asJava, false)

  private implicit val taskSerdes: SpecificAvroSerde[Task] = new SpecificAvroSerde[Task]()
  taskSerdes.configure(serdeConfig.asJava, false)

  private implicit val genConsumed: Consumed[String, SpecificRecord] = Consumed.`with`[String, SpecificRecord]
  private implicit val rewRejProduced: Produced[String, ReviewRejected] = Produced.`with`[String, ReviewRejected]
  private implicit val rewAppProduced: Produced[String, ReviewApproved] = Produced.`with`[String, ReviewApproved]
  private implicit val revProduced: Produced[String, Review] = Produced.`with`[String, Review]
  private implicit val taskProduced: Produced[String, Task] = Produced.`with`[String, Task]
  private implicit val joinedSerdes: Joined[String, SecurityCheckProcessed, Task] = Joined.`with`[String, SecurityCheckProcessed, Task]
  private implicit val taskConsumed: Consumed[String, Task] = Consumed.`with`[String, Task]

  private val streams = new KafkaStreams(createTopology, streamProps)

  override def start(): Unit = {
    LOG.info(s"Starting $applicationName Service")

    if (!started) {
      started = true
      streams.cleanUp()
      streams.start()
    }
  }

  override def stop(): Unit = {
    LOG.info(s"Stopping $applicationName Service")
    if (started) { started = false; streams.close() }
  }

  private def streamProps: Properties = {
    val props = new Properties()
    props.put(StreamsConfig.APPLICATION_ID_CONFIG, config.getString("kafka.streams.application.id"))
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, config.getString("kafka.bootstrap.servers"))
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE)
    props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1000) // 1 second
    props.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 4194304) // 250 KB
    props
  }

  private def createTopology: Topology = {
    val builder = new StreamsBuilder()
    val securityEventsource = config.getString("kafka.security.eventsource.topic.name")
    val reviewsEventsource = config.getString("kafka.reviews.eventsource.topic.name")
    val reviewsChangelog = config.getString("kafka.reviews.changelog.topic.name")
    val tasksChangelog = config.getString("kafka.tasks.changelog.topic.name")

    val inputSources = builder.stream[String, SpecificRecord](Set(securityEventsource, reviewsEventsource))
    val tasksTable = builder.table[String, Task](tasksChangelog)

    // Processing SecurityCheck results

    val securityCheckProcessed = inputSources
      .filter((_, v) => v match {
        case _: SecurityCheckProcessed => true
        case _ => false })
      .mapValues(_.asInstanceOf[SecurityCheckProcessed])

    val branches = securityCheckProcessed
      .branch(
        (_, v) => v.getStatus == "INVALID",
        (_, v) => v.getStatus == "VALID")

    branches(0)
      .mapValues(rec => ReviewRejected.newBuilder
        .setTaskId(rec.getTaskId)
        .setReview(Review.newBuilder(rec.getReview).setStatus(ReviewStatus.REJECTED).build())
        .setFailureMessage(rec.getFailureMessage)
        .build())
      .peek((_, v) => LOG.info(s"Rejected review $v"))
      .to(reviewsEventsource)

    branches(1)
      .mapValues(rec => ReviewApproved.newBuilder
        .setTaskId(rec.getTaskId)
        .setReview(Review.newBuilder(rec.getReview).setStatus(ReviewStatus.APPROVED).build())
        .build())
      .peek((_, v) => LOG.info(s"Approved review $v"))
      .to(reviewsEventsource)

    // Processing Reviews

    val reviewsLog = inputSources
      .filter((_, v) => v match {
        case _: ReviewApproved => true
        case _: ReviewRejected => true
        case _ => false })
      .mapValues(v => v match {
        case approved: ReviewApproved => approved.getReview
        case _ => v.asInstanceOf[ReviewRejected].getReview })

    reviewsLog
      .peek((_, v) => LOG.info(s"Added Review to changelog: $v"))
      .to(reviewsChangelog)


    // Updating tasks

    val taskUpdates = securityCheckProcessed
      .selectKey((_, v) => v.getTaskId)
      .join(tasksTable)(taskJoiner)

    taskUpdates
      .to(tasksChangelog)

    val tasksLogger = tasksTable.toStream
    tasksLogger.foreach((_, v) => LOG.info(s"Task Update: $v"))

    builder.build()
  }

  def taskJoiner(event: SecurityCheckProcessed, task: Task): Task = Task.newBuilder(task)
    .setStatus("DONE")
    .setErrorMessage(event.getFailureMessage)
    .setUpdatedAt(java.time.Instant.now().toEpochMilli)
    .build()
}

class ReviewsAppDaemon extends AbstractApplicationDaemon {
  override def application: ApplicationLifecycle = new ReviewsApp
}

object ReviewsService extends App {
  private val daemon = new ReviewsApp
  private[this] var cleanedUp = false
  private def cleanup(): Unit = { val previouslyRun = cleanedUp; cleanedUp = true; if (!previouslyRun) daemon.stop() }
  Runtime.getRuntime.addShutdownHook(new Thread(() => cleanup()))
  daemon.start()
}
