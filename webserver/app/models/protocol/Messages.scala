package models.protocol

import java.util.UUID
import common.avro.reviews.entities.{Review => ReviewDataModel}
import common.avro.security.events.{SecurityCheckRequested => SecurityCheckRequestedDataModel}
import models.DomainModel
import models.data.Review
import org.apache.avro.specific.SpecificRecord
import play.api.libs.json.{Json, OFormat}

object Messages {
  object Events {
    // SecurityCheckRequested
    case class SecurityCheckRequested(review: Review) extends DomainModel {

      override def toDataModel: SpecificRecord = {
        SecurityCheckRequestedDataModel.newBuilder
          .setTaskId("NotSet")
          .setReview(review.toDataModel.asInstanceOf[ReviewDataModel])
          .build()
      }
    }

    object SecurityCheckRequested {
      implicit val format: OFormat[SecurityCheckRequested] = Json.format[SecurityCheckRequested]
    }
  }

  object Queries {
    case class GetProductReviews(productId: String)
    case class GetReview(reviewId: String)
    case class GetTask(taskId: String)
    case class GetStats(productId: String)
  }
}
