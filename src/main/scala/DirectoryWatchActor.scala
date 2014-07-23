package org.encalmo.tagstatisticsakka

import akka.actor.{ Actor, ActorRef, Props, ActorSystem, ActorLogging }
import java.nio.file.{Path,FileSystem,WatchKey}

object FileWatchMessages {
  
  case class WatchPath(path: Path)
  case class WatchPathAck(path: Path)
  
}

class DirectoryWatchActor extends Actor with ActorLogging {
  
  val watchers = scala.collection.mutable.Map[FileSystem, ActorRef]()
  
  import FileWatchMessages._
  
  def receive = {
    case message @ WatchPath(path) if path.toFile().isDirectory() => {
      val fileSystem = path.getFileSystem()
      val worker = watchers.getOrElseUpdate(fileSystem, {
        context.actorOf(Props(classOf[FileSystemWatcherWorker],fileSystem))
      })
      worker.forward(message);
    }
  }

}

class FileSystemWatcherWorker(val fileSystem:FileSystem) extends Actor with ActorLogging {
  
  val log = 
  val watchService = fileSystem.newWatchService()
  val observers = scala.collection.mutable.Map[Path, List[ActorRef]]().withDefaultValue(Nil)
  
  import FileWatchMessages._
  import java.nio.file.StandardWatchEventKinds._
  
  def receive = {
    case WatchPath(path) if !(observers(path).contains(sender)) => {
      observers(path) = sender :: observers(path)
      val watchKey = path.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
      sender ! WatchPathAck(path)
    }
  }
}