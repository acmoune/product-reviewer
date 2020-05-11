package models.data

import java.time.Instant
import java.util.UUID

import common.avro.reviews.entities.{ReviewStatus, Review => ReviewDataModel}
import models.DomainModel
import org.apache.avro.specific.SpecificRecord
import play.api.libs.json.{Json, OFormat}

case class Review(productId: String,
                  productName: String,
                  username: String,
                  userEmail: String,
                  rating: Int,
                  userId: Option[String] = None,
                  content: Option[String] = None,
                  createdAt: Option[Long] = None,
                  reviewId: Option[String] = None,
                  status: Option[String] = None) extends DomainModel {

  override def toDataModel: SpecificRecord = {
    ReviewDataModel.newBuilder()
      .setProductId(productId)
      .setCreatedAt(Instant.now().toEpochMilli)
      .setStatus(ReviewStatus.PROCESSING)
      .setReviewId(UUID.randomUUID().toString)
      .setRating(rating)
      .setProductName(productName)
      .setContent(content.getOrElse(""))
      .setUserId(UUID.randomUUID().toString)
      .setUsername(username)
      .setUserEmail(userEmail)
      .build()
  }
}

object Review {
  implicit val format: OFormat[Review] = Json.format[Review]
}
