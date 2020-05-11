package services

import java.util.{Properties, UUID}

import akka.actor.{Actor, ActorRef}
import akka.pattern.pipe
import akka.util.Timeout
import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import io.confluent.kafka.serializers.{AbstractKafkaAvroSerDeConfig, KafkaAvroSerializer}
import common.avro.security.events.{SecurityCheckRequested => SecurityCheckRequestedDataModel}
import models.data.Task
import models.protocol.Messages.Events.SecurityCheckRequested
import models.protocol.Messages.Queries.{GetProductReviews, GetReview, GetStats, GetTask}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import play.api.{Configuration, Logging}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import common.avro.{Task => TaskDataModel}

@Singleton
class ReviewManager @Inject()(config: Configuration,
                              @Named("review-repository") reviewRepo: ActorRef)
                             (implicit ec: ExecutionContext) extends Actor with Logging {

  val securityProducer = new KafkaProducer[String, SecurityCheckRequestedDataModel](producerProps)
  val tasksProducer = new KafkaProducer[String, TaskDataModel](producerProps)
  val securityEventsource: String = config.get[String]("kafka.security.eventsource.topic.name")
  val tasksChangelog: String = config.get[String]("kafka.tasks.changelog.topic.name")
  implicit val timeout: Timeout = 60.seconds

  override def receive: Receive = {
    case e: SecurityCheckRequested =>
      val taskId = UUID.randomUUID().toString
      val event = e.toDataModel.asInstanceOf[SecurityCheckRequestedDataModel]
      val task = Task.newTask(taskId = taskId, resourceId = event.getReview.getReviewId)
      val taskDM = task.toDataModel.asInstanceOf[TaskDataModel]
      event.setTaskId(taskId)

      val securityCheckRecord = new ProducerRecord[String, SecurityCheckRequestedDataModel](securityEventsource, event.getReview.getReviewId, event)
      val taskRecord = new ProducerRecord[String, TaskDataModel](tasksChangelog, task.taskId, taskDM)

      val fut1 = Future { tasksProducer.send(taskRecord).get }
      val fut2 = Future { securityProducer.send(securityCheckRecord).get }

      val ops = for {
        _ <- fut1
        _ <- fut2
      } yield task

      ops.pipeTo(sender)

    case query: GetProductReviews =>
      reviewRepo.forward(query)

    case query: GetReview =>
      reviewRepo.forward(query)

    case query: GetTask =>
      reviewRepo.forward(query)

    case query: GetStats =>
      reviewRepo.forward(query)
  }

  def producerProps: Properties = {
    val props = new Properties()
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.get[String]("kafka.bootstrap.servers"))
    props.put(ProducerConfig.ACKS_CONFIG, "all")
    props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE)
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[KafkaAvroSerializer].getName)
    props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5)
    props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy")
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
    props.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, config.get[String]("kafka.schema.registry.url"))
    props.put(AbstractKafkaAvroSerDeConfig.VALUE_SUBJECT_NAME_STRATEGY, classOf[TopicRecordNameStrategy].getName)
    props
  }

  override def postStop(): Unit = {
    tasksProducer.flush()
    tasksProducer.close()
    securityProducer.flush()
    securityProducer.close()
    super.postStop()
  }
}
