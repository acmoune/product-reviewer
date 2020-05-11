package sources

import akka.NotUsed
import akka.actor.ActorSystem
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.kafka.scaladsl.Consumer
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Keep, Source}
import com.google.inject.{Inject, Singleton}
import common.avro.reviews.events.ReviewApproved
import io.confluent.kafka.serializers.{AbstractKafkaAvroSerDeConfig, KafkaAvroDeserializer, KafkaAvroDeserializerConfig}
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import models.data.Review
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.common.serialization.{Deserializer, StringDeserializer}
import play.api.{Configuration, Logging}
import play.api.libs.json.{JsValue, Json}
import collection.JavaConverters._
import scala.concurrent.ExecutionContext


@Singleton
class ReviewsSource @Inject()(system: ActorSystem,
                              config: Configuration)
                             (implicit materialize: Materializer,
                              ec: ExecutionContext) extends Logging {

  private val reviewsTopic = config.get[String]("kafka.reviews.eventsource.topic.name")

  private val consumerSettings: ConsumerSettings[String, SpecificRecord] = {
    val serDeConfig = Map[String, Any](
      AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG -> config.get[String]("kafka.schema.registry.url"),
      KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG -> true.toString,
      AbstractKafkaAvroSerDeConfig.VALUE_SUBJECT_NAME_STRATEGY -> classOf[TopicRecordNameStrategy].getName)

    val kafkaAvroDeserializer = new KafkaAvroDeserializer()
    kafkaAvroDeserializer.configure(serDeConfig.asJava, false)
    val deserializer = kafkaAvroDeserializer.asInstanceOf[Deserializer[SpecificRecord]]
    ConsumerSettings(system, new StringDeserializer, deserializer)
  }

  val source: Source[JsValue, NotUsed] = {
    Consumer
      .plainSource[String, SpecificRecord](consumerSettings, Subscriptions.topics(reviewsTopic))
      .filter(record => record.value() match {
        case _: ReviewApproved => true
        case _ => false })
      .map(event => {
        val review = event.value().asInstanceOf[ReviewApproved].getReview

        Json.toJson(Review(
          productId = review.getProductId,
          productName = review.getProductName,
          userId = Option(review.getUserId),
          username = review.getUsername,
          userEmail = review.getUserEmail,
          rating = review.getRating,
          content = Option(review.getContent),
          createdAt = Option(review.getCreatedAt),
          reviewId = Option(review.getReviewId),
          status = Option(review.getStatus.toString)))
        })
      .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right)
      .run()
  }
}
