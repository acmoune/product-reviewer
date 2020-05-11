package models.data

import play.api.libs.json.{Json, OFormat}

case class ProductStatistic(productId: String,
                            productTitle: String,
                            countOne: Int,
                            countTwo: Int,
                            countThree: Int,
                            countFour: Int,
                            countFive: Int,
                            totalReviews: Long,
                            totalRating: Long)

object ProductStatistic {
  implicit val format: OFormat[ProductStatistic] = Json.format[ProductStatistic]
}
