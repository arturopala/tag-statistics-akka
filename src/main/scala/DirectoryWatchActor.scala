package org.encalmo.tagstatisticsakka

import akka.actor.{ Actor, ActorRef, Props, ActorSystem, ActorLogging,Terminated,PoisonPill }
import java.nio.file.{ Path, FileSystem, WatchKey, WatchService }

object Messages {

  trait Failure
  trait Command
  trait Event
  case class WatchPath(path: Path) extends Command
  case class UnwatchPath(path: Path) extends Command
  case class WatchPathAck(path: Path) extends Event
  case class UnwatchPathAck(path: Path) extends Event
  case class WatchPathNotValid(path: Path) extends Event with Failure
  case class FileCreated(path: Path) extends Event
  case class FileModified(path: Path) extends Event
  case class FileDeleted(path: Path) extends Event
}

/** Actor responsible for watching file events (created, modified, deleted) on registered paths */
class DirectoryWatchActor extends Actor with ActorLogging {

  val workerMap = scala.collection.mutable.Map[FileSystem, ActorRef]()

  def receive = {
    case message @ Messages.WatchPath(path) => {
      val file = path.toFile()
      if (!file.exists() || !file.isDirectory()) {
        sender ! Messages.WatchPathNotValid(path)
      } else {
        val fileSystem = path.getFileSystem()
        val worker = workerMap.getOrElseUpdate(fileSystem, {
          context.actorOf(Props(classOf[FileSystemWatcherWorker], fileSystem))
        })
        worker.forward(message);
      }
    }
  }

}

/** Actor's worker responsible for watching file events from selected filesystem */
class FileSystemWatcherWorker(val fileSystem: FileSystem) extends Actor with ActorLogging {

  val observers = scala.collection.mutable.Map[Path, List[ActorRef]]().withDefaultValue(Nil)
  var worker = context.actorOf(Props(classOf[WatchServiceWorker], fileSystem.newWatchService(), observers))

  def receive = {
    case message @ Messages.WatchPath(path) if !(observers(path).contains(sender)) => {
      observers(path) = sender :: observers(path)
      context.watch(sender) //if sender terminates must then unregister watched path
      worker.forward(message)
    }
    case Terminated(that) => {
      observers.find {
        case (path,refs) => refs.contains(that)
      } foreach {
        case (path, _) => self ! (Messages.UnwatchPath(path), that)
      }
    }
  }
}

object WatchServiceWorker {
  case class ListeningFailed(exception: Throwable)
}

/** Actor's worker directly responsible for watching file events */
class WatchServiceWorker(val watchService: WatchService, val observers: scala.collection.Map[Path, List[ActorRef]]) extends Actor with ActorLogging {
  import java.nio.file.StandardWatchEventKinds._

  val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
  val watchKey2PathMap = scala.collection.mutable.Map[WatchKey, Path]()
  
  def receive = {
    case Messages.WatchPath(path) => {
      val watchKey = path.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
      watchKey2PathMap(watchKey) = path
      sender ! Messages.WatchPathAck(path)
    }
    case WatchServiceWorker.ListeningFailed(exception) => throw new RuntimeException(exception)
  }

  def watch = {
    import scala.collection.JavaConversions._
    try {
	    while (!Thread.interrupted()) {
	      val nextKey = watchService.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS)
	      Option(nextKey) foreach { key =>
	        watchKey2PathMap.get(key) foreach { path =>
	          observers.get(path) foreach { refs =>
	            val events = key.pollEvents()
	            for (event <- events) {
	              val file = path.resolve(event.context().asInstanceOf[Path])
	              event.kind() match {
	                case ENTRY_CREATE => refs.foreach { _ ! Messages.FileCreated(file) }
	                case ENTRY_MODIFY => refs.foreach { _ ! Messages.FileModified(file) }
	                case ENTRY_DELETE => refs.foreach { _ ! Messages.FileDeleted(file) }
	                case _ => //unsupported events
	              }
	            }
	          }
	        }
	        key.reset()
	      }
	    }
    }
    catch {
      case e: InterruptedException =>
      case e: Throwable => self ! WatchServiceWorker.ListeningFailed(e)
    }
  }

  override def preStart() = {
    //re-register observers 
    observers.keys foreach {path => self ! Messages.WatchPath(path)}
    //run watching thread
    executor.execute(new Runnable {
      def run = watch
    })
  }

  override def postStop() = {
    executor.shutdownNow()
  }

}