package modules

import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

import services.ReviewManager
import repositories.ReviewRepo

class AppModule extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    bindActor[ReviewManager]("review-manager")
    bindActor[ReviewRepo]("review-repository")
  }
}
