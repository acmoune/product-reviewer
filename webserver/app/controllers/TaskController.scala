package controllers

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.google.inject.Inject
import com.google.inject.name.Named
import groovy.lang.Singleton
import models.data.Task
import models.protocol.Messages.Queries.GetTask
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

@Singleton
class TaskController @Inject()(cc: ControllerComponents,
                               @Named("review-manager") reviewManager: ActorRef)
                              (implicit ec: ExecutionContext) extends AbstractController(cc) {

  implicit val timeout: Timeout = 60.seconds

  def getTask(taskId: String): Action[AnyContent] = Action.async {
    (reviewManager ? GetTask(taskId))
      .mapTo[Option[Task]]
      .map { task => Ok(Json.toJson(task)) }
  }
}
