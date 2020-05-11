package sources

import akka.NotUsed
import akka.actor.ActorSystem
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.kafka.scaladsl.Consumer
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Keep, Source}
import com.google.inject.{Inject, Singleton}
import common.avro.statistics.entities.ProductStatistic
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import io.confluent.kafka.serializers.{AbstractKafkaAvroSerDeConfig, KafkaAvroDeserializer, KafkaAvroDeserializerConfig}
import models.data.{ProductStatistic => ProductStatisticModel}
import org.apache.kafka.common.serialization.{Deserializer, StringDeserializer}
import play.api.libs.json.{JsValue, Json}
import play.api.{Configuration, Logging}

import collection.JavaConverters._
import scala.concurrent.ExecutionContext

@Singleton
class ProductStatsSource @Inject()(system: ActorSystem,
                                   config: Configuration)
                                  (implicit materialize: Materializer,
                                   ec: ExecutionContext) extends Logging {

  private val statsTopic = config.get[String]("kafka.stats.changelog.topic.name")

  private val consumerSettings: ConsumerSettings[String, ProductStatistic] = {
    val serDeConfig = Map[String, Any](
      AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG -> config.get[String]("kafka.schema.registry.url"),
      KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG -> true.toString,
      AbstractKafkaAvroSerDeConfig.VALUE_SUBJECT_NAME_STRATEGY -> classOf[TopicRecordNameStrategy].getName)

    val kafkaAvroDeserializer = new KafkaAvroDeserializer()
    kafkaAvroDeserializer.configure(serDeConfig.asJava, false)
    val deserializer = kafkaAvroDeserializer.asInstanceOf[Deserializer[ProductStatistic]]
    ConsumerSettings(system, new StringDeserializer, deserializer)
  }

  val source: Source[JsValue, NotUsed] = {
    Consumer
      .plainSource[String, ProductStatistic](consumerSettings, Subscriptions.topics(statsTopic))
      .filter(record => record.key() == config.get[String]("app.product.id"))
      .map(record => record.value())
      .map(stat =>
        Json.toJson(ProductStatisticModel(
          productId = stat.getProductId,
          productTitle = stat.getProductTitle,
          countOne = stat.getCountOne,
          countTwo = stat.getCountTwo,
          countThree = stat.getCountThree,
          countFour = stat.getCountFour,
          countFive = stat.getCountFive,
          totalReviews = stat.getTotalReviews,
          totalRating = stat.getTotalRating))
      )
      .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right)
      .run()
  }
}
