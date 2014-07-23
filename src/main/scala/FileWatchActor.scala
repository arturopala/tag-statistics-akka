package org.encalmo.tagstatisticsakka

import akka.actor.{ Actor, Props, ActorSystem, ActorLogging }

case class WatchFolder(path: String)

class FileWatchActor extends Actor with ActorLogging {
  
  def receive = {
    case WatchFolder(path) =>
    case _ =>
  }

}