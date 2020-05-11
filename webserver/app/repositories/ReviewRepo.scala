package repositories

import akka.actor.Actor
import akka.util.Timeout
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.Row
import com.google.inject.{Inject, Singleton}
import common.avro.reviews.entities.ReviewStatus
import models.data.{ProductStatistic, Review, Task}
import models.protocol.Messages.Queries.{GetProductReviews, GetReview, GetTask, GetStats}
import play.api.Logging

import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}


@Singleton
class ReviewRepo @Inject()()(implicit ec: ExecutionContext) extends Actor with Logging {
  import akka.pattern.pipe

  implicit val timeout: Timeout = 60.seconds
  private val session = CqlSession.builder().build()

  override def receive: Receive = {
    case GetProductReviews(productId) =>
      getReviewsByProduct(productId).pipeTo(sender())

    case GetReview(reviewId) =>
      getReview(reviewId).pipeTo(sender())

    case GetTask(taskId) =>
      getTask(taskId).pipeTo(sender())

    case GetStats(productId) =>
      getStats(productId).pipeTo(sender())
  }

  private def getReviewsByProduct(productId: String): Future[List[Review]] =
    Future { session.execute(s"select * from reviews_by_product where product_id='$productId' and status='${ReviewStatus.APPROVED.toString}'") }
      .map { _.asScala.toList.map { row => rowToReview(row) } }

  private def getReview(reviewId: String): Future[Option[Review]] =
    Future { session.execute(s"select * from reviews where review_id='$reviewId' and status='${ReviewStatus.APPROVED.toString}'").one() }
      .map { row => if (row == null) None else Some(rowToReview(row)) }

  private def rowToReview(row: Row): Review =
    Review(
      productId = row.getString("product_id"),
      productName = row.getString("product_name"),
      userId = Option(row.getString("user_id")),
      username = row.getString("username"),
      userEmail = row.getString("user_email"),
      rating = row.getInt("rating"),
      content = Option(row.getString("content")),
      createdAt = Option(row.getLong("created_at")),
      reviewId = Option(row.getString("review_id")),
      status = Option(row.getString("status"))
    )

  private def getTask(taskId: String): Future[Option[Task]] =
    Future { session.execute(s"select * from tasks where task_id='$taskId'").one() }
      .map { row =>
        if (row == null)
          None
        else
          Some(Task(
            taskId = taskId,
            resourceId = row.getString("resource_id"),
            status = row.getString("status"),
            errorMessage = Option(row.getString("error_message")),
            createdAt = row.getLong("created_at"),
            updatedAt = Option(row.getLong("updated_at"))
          ))
      }

  private def getStats(productId: String): Future[Option[ProductStatistic]] =
    Future { session.execute(s"select * from stats where product_id='$productId'").one() }
      .map { row =>
        if (row == null)
          None
        else
          Some(ProductStatistic(
            productId = productId,
            productTitle = row.getString("product_title"),
            countOne = row.getInt("count_one"),
            countTwo = row.getInt("count_two"),
            countThree = row.getInt("count_three"),
            countFour = row.getInt("count_four"),
            countFive = row.getInt("count_five"),
            totalReviews = row.getLong("total_reviews"),
            totalRating = row.getLong("total_rating")
          ))
      }
}
