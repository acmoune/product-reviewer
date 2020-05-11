import java.util.Properties
import common.daemon._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig, Topology}
import org.slf4j.{Logger, LoggerFactory}
import com.typesafe.config.{Config, ConfigFactory}
import common.avro.reviews.entities.Review
import common.avro.reviews.events.ReviewApproved
import common.avro.statistics.entities.ProductStatistic
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import org.apache.kafka.streams.scala.{ByteArrayKeyValueStore, StreamsBuilder}
import org.apache.kafka.streams.scala.kstream.{Consumed, Grouped, Materialized, Produced}
import org.apache.kafka.streams.scala.Serdes._
import org.apache.avro.specific.SpecificRecord
import collection.JavaConverters._


class StatisticsApp extends ApplicationLifecycle {
  implicit val LOG: Logger = LoggerFactory.getLogger("statistics-app")
  private val applicationName = "Statistics"
  implicit val config: Config = ConfigFactory.load()
  private[this] var started = false

  private val serdeConfig = Map[String, Any](
    "schema.registry.url"-> config.getString("kafka.schema.registry.url"),
    "specific.avro.reader"-> true,
    "value.subject.name.strategy"-> classOf[TopicRecordNameStrategy].getName)

  private implicit val specificSerdes: SpecificAvroSerde[SpecificRecord] = new SpecificAvroSerde[SpecificRecord]()
  specificSerdes.configure(serdeConfig.asJava, false)

  private implicit val revSerdes: SpecificAvroSerde[Review] = new SpecificAvroSerde[Review]()
  revSerdes.configure(serdeConfig.asJava, false)

  private implicit val proStatSerdes: SpecificAvroSerde[ProductStatistic] = new SpecificAvroSerde[ProductStatistic]()
  proStatSerdes.configure(serdeConfig.asJava, false)

  private implicit val genConsumed: Consumed[String, SpecificRecord] = Consumed.`with`[String, SpecificRecord]
  private implicit val revGrouped: Grouped[String, Review] = Grouped.`with`[String, Review]
  private implicit val statProduced: Produced[String, ProductStatistic] = Produced.`with`[String, ProductStatistic]
  private implicit val statConsumed: Consumed[String, ProductStatistic] = Consumed.`with`[String, ProductStatistic]
  private implicit val aggMaterialized: Materialized[String, ProductStatistic, ByteArrayKeyValueStore] = Materialized.as("reviewsAggregateStore")
  
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

    if (started) {
      started = false
      streams.close()
    }
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
    val reviewsEventsource = config.getString("kafka.reviews.eventsource.topic.name")
    val statsChangelog = config.getString("kafka.stats.changelog.topic.name")

    val inputStreams = builder.stream[String, SpecificRecord](reviewsEventsource)

    val approvedReviews = inputStreams
      .filter((_, v) => v match {
        case _: ReviewApproved => true
        case _ => false })
      .mapValues(_.asInstanceOf[ReviewApproved].getReview)

    val stats = approvedReviews
      .selectKey((_, review) => review.getProductId)
      .groupByKey
      .aggregate(emptyStat)(reviewAggregator)

    stats
      .toStream
      .to(statsChangelog)

    builder.table[String, ProductStatistic](statsChangelog)
      .toStream
      .foreach((_, v) => LOG.info(s"Statistic Update: $v"))

    builder.build()
  }

  private def emptyStat = ProductStatistic.newBuilder()
    .setProductId("")
    .setProductTitle("")
    .setCountOne(0)
    .setCountTwo(0)
    .setCountThree(0)
    .setCountFour(0)
    .setCountFive(0)
    .setTotalReviews(0)
    .setTotalRating(0)
    .build()

  private def reviewAggregator(productId: String,
                               newReview: Review,
                               currentStat: ProductStatistic
                              ): ProductStatistic = {

    val productStatsBuilder = ProductStatistic.newBuilder(currentStat)

    productStatsBuilder.setProductId(productId)
    productStatsBuilder.setProductTitle(newReview.getProductName)

    newReview.getRating match {
      case 1 => productStatsBuilder.setCountOne(productStatsBuilder.getCountOne + 1)
      case 2 => productStatsBuilder.setCountTwo(productStatsBuilder.getCountTwo + 1)
      case 3 => productStatsBuilder.setCountThree(productStatsBuilder.getCountThree + 1)
      case 4 => productStatsBuilder.setCountFour(productStatsBuilder.getCountFour + 1)
      case 5 => productStatsBuilder.setCountFive(productStatsBuilder.getCountFive + 1)
    }

    productStatsBuilder.setTotalReviews(productStatsBuilder.getTotalReviews + 1)
    productStatsBuilder.setTotalRating(productStatsBuilder.getTotalRating + newReview.getRating)

    productStatsBuilder.build()
  }
}

class StatisticsAppDaemon extends AbstractApplicationDaemon {
  override def application: ApplicationLifecycle = new StatisticsApp
}

object StatisticsService extends App {
  private val daemon = new StatisticsAppDaemon
  private[this] var cleanedUp = false
  private def cleanup(): Unit = { val previouslyRun = cleanedUp; cleanedUp = true; if (!previouslyRun) daemon.stop() }
  Runtime.getRuntime.addShutdownHook(new Thread(() => cleanup()))
  daemon.start()
}
