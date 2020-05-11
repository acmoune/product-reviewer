import java.util.Properties
import com.google.common.collect.ImmutableMap
import common.daemon._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig, Topology}
import org.slf4j.{Logger, LoggerFactory}
import com.typesafe.config.ConfigFactory
import common.avro.reviews.entities.Review
import common.avro.security.events.{SecurityCheckProcessed, SecurityCheckRequested}
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.streams.scala.kstream.{Consumed, Produced}
import org.apache.kafka.streams.scala.Serdes._
import common.util.Validator
import org.apache.avro.specific.SpecificRecord


class SecurityApp extends ApplicationLifecycle {
  private val LOG: Logger = LoggerFactory.getLogger("security-app")
  private[this] var started = false
  private val applicationName = "Security"
  private val config = ConfigFactory.load()

  private val serdeConfig = ImmutableMap.of(
    "schema.registry.url", config.getString("kafka.schema.registry.url"),
    "specific.avro.reader", true,
    "value.subject.name.strategy", classOf[TopicRecordNameStrategy].getName
  )

  private implicit val specificSerdes: SpecificAvroSerde[SpecificRecord] = new SpecificAvroSerde[SpecificRecord]()
  specificSerdes.configure(serdeConfig, false)

  private implicit val secReqProcSerdes: SpecificAvroSerde[SecurityCheckProcessed] = new SpecificAvroSerde[SecurityCheckProcessed]()
  secReqProcSerdes.configure(serdeConfig, false)

  private implicit val secConsumed: Consumed[String, SpecificRecord] = Consumed.`with`[String, SpecificRecord]
  private implicit val secProduced: Produced[String, SecurityCheckProcessed] = Produced.`with`[String, SecurityCheckProcessed]

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

    builder.stream[String, SpecificRecord](securityEventsource)
      .filter((_, v) => v match {
        case _: SecurityCheckRequested => true
        case _ => false })
      .mapValues { v =>
        val req = v.asInstanceOf[SecurityCheckRequested]
        val isValid = isSecuredReview(req.getReview)
        val failureMessage = if (isValid) "" else "The reviewer email is not valid"

        SecurityCheckProcessed.newBuilder
          .setTaskId(req.getTaskId)
          .setReview(req.getReview)
          .setStatus(if (isValid) "VALID" else "INVALID")
          .setFailureMessage(failureMessage)
          .build() }
      .peek((_, v) => LOG.info(s"Security Check performed for $v"))
      .to(securityEventsource)

    builder.build()
  }

  private def isSecuredReview(review: Review): Boolean = Validator.checkEmail(review.getUserEmail)
}

class SecurityAppDaemon extends AbstractApplicationDaemon {
  override def application: ApplicationLifecycle = new SecurityApp
}

object SecurityService extends App {
  private val daemon = new SecurityAppDaemon
  private[this] var cleanedUp = false
  private def cleanup(): Unit = { val previouslyRun = cleanedUp; cleanedUp = true; if (!previouslyRun) daemon.stop() }
  Runtime.getRuntime.addShutdownHook(new Thread(() => cleanup()))
  daemon.start()
}
