package common.daemon

import org.apache.commons.daemon.{Daemon, DaemonContext}

abstract class AbstractApplicationDaemon extends Daemon  {
  def application: ApplicationLifecycle

  override def init(context: DaemonContext): Unit = {}

  override def start(): Unit = application.start()

  override def stop(): Unit = application.stop()

  override def destroy(): Unit = application.stop()
}
