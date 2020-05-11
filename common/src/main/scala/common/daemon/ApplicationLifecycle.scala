package common.daemon

trait ApplicationLifecycle {
  def start(): Unit
  def stop(): Unit
}
