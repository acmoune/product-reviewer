package models.data

import models.DomainModel
import org.apache.avro.specific.SpecificRecord
import play.api.libs.json.{Json, OFormat}
import common.avro.{Task => TaskDataModel}

case class Task(taskId: String,
                resourceId: String,
                status: String,
                errorMessage: Option[String] = None,
                createdAt: Long,
                updatedAt: Option[Long] = None) extends DomainModel {

  override def toDataModel: SpecificRecord = {
    TaskDataModel.newBuilder()
      .setTaskId(taskId)
      .setResourceId(resourceId)
      .setStatus(status)
      .setErrorMessage(errorMessage.getOrElse(""))
      .setCreatedAt(createdAt)
      .setUpdatedAt(if (updatedAt.isDefined) updatedAt.get else null)
      .build()
  }
}

object Task {
  implicit val format: OFormat[Task] = Json.format[Task]

  def newTask(taskId: String, resourceId: String): Task = Task(
    taskId = taskId,
    resourceId = resourceId,
    status = "PROCESSING",
    createdAt = java.time.Instant.now().toEpochMilli
  )
}
