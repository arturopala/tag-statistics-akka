package org.encalmo.tagstatisticsakka

import akka.actor.{ Actor, ActorRef, Props, ActorSystem, ActorLogging }
import java.nio.file.{Path,FileSystem,WatchKey,WatchService}

object Messages {
  
  trait Failure
  case class WatchPath(path: Path)
  case class WatchPathAck(path: Path)
  case class WatchPathNotValid(path: Path) extends Failure
  
}

class DirectoryWatchActor extends Actor with ActorLogging {
  
  val workerMap = scala.collection.mutable.Map[FileSystem, ActorRef]()
  
  def receive = {
    case message @ Messages.WatchPath(path) => {
      val file = path.toFile()
      if (!file.exists() || !file.isDirectory()) {
        sender ! Messages.WatchPathNotValid(path)
      }
      else {
	      val fileSystem = path.getFileSystem()
	      val worker = workerMap.getOrElseUpdate(fileSystem, {
	        context.actorOf(Props(classOf[FileSystemWatcherWorker], fileSystem))
	      })
	      worker.forward(message);
      }
    }
  }
  
}

class FileSystemWatcherWorker(val fileSystem:FileSystem) extends Actor with ActorLogging {
  
  val observers = scala.collection.mutable.Map[Path, List[ActorRef]]().withDefaultValue(Nil)
  var worker = context.actorOf(Props(classOf[WatchServiceWorker], fileSystem.newWatchService(), observers))
  
  def receive = {
    case message @ Messages.WatchPath(path) if !(observers(path).contains(sender)) => {
      observers(path) = sender :: observers(path)
      worker.forward(message)
    }
  }
}

class WatchServiceWorker(val watchService: WatchService, val observers: scala.collection.Map[Path, List[ActorRef]]) extends Actor with ActorLogging {
  import java.nio.file.StandardWatchEventKinds._
  
  
  
  def receive = {
    case Messages.WatchPath(path) => {
      val watchKey = path.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
      sender ! Messages.WatchPathAck(path)
    }
  }
}