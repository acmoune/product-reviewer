package controllers

import akka.actor.ActorRef
import akka.pattern._
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink}
import akka.util.Timeout

import scala.concurrent.duration._
import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import models.data.{ProductStatistic, Review, Task}
import models.protocol.Messages.Events.SecurityCheckRequested
import models.protocol.Messages.Queries.{GetProductReviews, GetReview, GetStats}
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents, WebSocket}
import play.api.Logging
import play.api.mvc.WebSocket.MessageFlowTransformer
import sources.{ProductStatsSource, ReviewsSource}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReviewController @Inject()(cc: ControllerComponents,
                                 @Named("review-manager") reviewManager: ActorRef,
                                 reviewsSource: ReviewsSource,
                                 statsSource: ProductStatsSource)
                                (implicit ec: ExecutionContext,
                                 materializer: Materializer) extends AbstractController(cc) with Logging {

  implicit val timeout: Timeout = 60.seconds

  def addReview(productId: String) = Action.async(parse.json) { implicit request =>
    request.body.validate[Review].fold(
      errors => Future.successful(BadRequest(Json.obj("status" -> "failed", "message" -> JsError.toJson(errors)))),
      data => {
        (reviewManager ? SecurityCheckRequested(data))
          .mapTo[Task]
          .map(task => Ok(Json.toJson(task)))
      }
    )
  }

  def getReviewsByProduct(productId: String): Action[AnyContent] = Action.async {
    (reviewManager ? GetProductReviews(productId))
      .mapTo[List[Review]]
      .map { list => Ok(Json.toJson(list)) }
  }

  def getReview(productId: String, reviewId: String): Action[AnyContent] = Action.async {
    (reviewManager ? GetReview(reviewId))
      .mapTo[Option[Review]]
      .map { review => Ok(Json.toJson(review)) }
  }

  def getStats(productId: String): Action[AnyContent] = Action.async {
    (reviewManager ? GetStats(productId))
      .mapTo[Option[ProductStatistic]]
      .map { stats => Ok(Json.toJson(stats)) }
  }

  def reviewsSocket: WebSocket = WebSocket.accept[JsValue, JsValue] { implicit request =>
    implicit val messageFlowTransformer = MessageFlowTransformer.jsonMessageFlowTransformer[JsValue, JsValue]
    Flow.fromSinkAndSource(Sink.ignore, reviewsSource.source)
  }

  def statsSocket(productId: String): WebSocket = WebSocket.accept[JsValue, JsValue] { implicit request =>
    implicit val messageFlowTransformer = MessageFlowTransformer.jsonMessageFlowTransformer[JsValue, JsValue]
    Flow.fromSinkAndSource(Sink.ignore, statsSource.source)
  }
}
