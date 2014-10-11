package code.arturopala.tagstatisticsakka.fileswatch

import akka.actor.{ Actor, ActorRef, Props, ActorSystem, ActorLogging,Terminated,PoisonPill }
import java.nio.file.{ Path, FileSystem, WatchKey, WatchService }

object Messages {

  trait Failure
  trait Command
  trait Event
  // path commands
  case class WatchPath(path: Path) extends Command
  case class UnwatchPath(path: Path) extends Command
  case class WatchPathAck(path: Path) extends Event
  case class UnwatchPathAck(path: Path) extends Event
  case class WatchPathNotValid(path: Path) extends Event with Failure
  // file events
  case class FileCreated(path: Path) extends Event
  case class FileModified(path: Path) extends Event
  case class FileDeleted(path: Path) extends Event
  
  object Internal {
    case class RefreshObservers(newObservers: Map[Path, Set[ActorRef]])
    case class WatchThreadFailed(exception: Throwable) extends Failure
  }
}

/** Actor responsible for watching file events (created, modified, deleted) on registered paths */
class FolderWatchActor extends Actor with ActorLogging {

  val workerMap = scala.collection.mutable.Map[FileSystem, ActorRef]()

  def receive = {
    case message @ Messages.WatchPath(path) => {
      val file = path.toFile
      if (!file.exists() || !file.isDirectory) {
        sender ! Messages.WatchPathNotValid(path)
      } else {
        val fileSystem = path.getFileSystem
        val worker = workerMap.getOrElseUpdate(fileSystem, {
          val newWorker = context.actorOf(Props(classOf[FolderWatchActorWorker], fileSystem), "worker-"+fileSystem.toString)
          context.watch(newWorker)
          newWorker
        })
        worker.forward(message)
      }
    }
    case message @ Messages.UnwatchPath(path) => {
      val fileSystem = path.getFileSystem
      workerMap.get(fileSystem) foreach { _.forward(message) }
    }
    case Terminated(that) => {
      workerMap find {case (_,ref) => ref == that} foreach {case (fs,_) => workerMap.remove(fs)}
    }
  }

}

/** Actor's worker responsible for watching file events from specified filesystem */
class FolderWatchActorWorker(val fileSystem: FileSystem) extends Actor with ActorLogging {
  
  var observers = Map[Path, Set[ActorRef]]().withDefaultValue(Set())
  val watcher = context.actorOf(Props(classOf[FolderWatchService], fileSystem.newWatchService(), observers),"watcher")

  def receive = {
    case message @ Messages.WatchPath(path) => {
      if (!(observers(path).contains(sender))){
	      context.watch(sender) //if sender terminates must then unregister watched path
	      observers = observers + ((path, observers(path) + sender))
	      watcher ! Messages.Internal.RefreshObservers(observers)
	      watcher.forward(message)
      }
    }
    case message @ Messages.UnwatchPath(path) => {
      if (observers(path).contains(sender)) {
	      context.unwatch(sender)
	      watcher.forward(message)
	      val newRefSet = observers(path) - sender
	      if(newRefSet.isEmpty){
	        observers = observers - path
	      } else {
	        observers = observers + ((path, newRefSet))
	      }
	      if(observers.isEmpty){
	        self ! PoisonPill
	      } else {
	        watcher ! Messages.Internal.RefreshObservers(observers)
	      }
      }
    }
    case Terminated(that) => {
      observers filter {
        case (_, refs) => refs.contains(that)
      } foreach {
        case (path, _) => self ! (Messages.UnwatchPath(path), that)
      }
    }
  }
  
}

/** Actor's service directly responsible for watching file events, manages watching loop in the separate thread */
class FolderWatchService(val watchService: WatchService, initialObservers: Map[Path, Set[ActorRef]]) extends Actor with ActorLogging {
  import java.nio.file.StandardWatchEventKinds._

  val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
  val watchKey2PathMap = scala.collection.mutable.Map[WatchKey, Path]()
  
  var observers = initialObservers
  
  def receive = {
    case Messages.Internal.RefreshObservers(newObservers) =>
      observers = newObservers
    case Messages.WatchPath(path) => {
      val watchKey = path.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
      watchKey2PathMap(watchKey) = path
      sender ! Messages.WatchPathAck(path)
      log.info(s"Folder watch registered: $path")
    }
    case Messages.UnwatchPath(path) => {
      watchKey2PathMap filter { case (_, p) => p == path} foreach {
        case (key, path) => {
          key.cancel()
          watchKey2PathMap.remove(key)
          sender ! Messages.UnwatchPathAck(path)
          log.info(s"Folder watch removed: $path")
        }
      }
    }
    case Messages.Internal.WatchThreadFailed(exception) => throw new RuntimeException(exception)
  }

  def watchFolder = {
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
                  case ENTRY_MODIFY => {
                    log.info(s"File modified: $file")
                    refs.foreach { _ ! Messages.FileModified(file) }
                  }
                  case ENTRY_CREATE => {
                    log.info(s"File created: $file")
                    refs.foreach { _ ! Messages.FileCreated(file) }
                  }
	                case ENTRY_DELETE => {
                    log.info(s"File deleted: $file")
                    refs.foreach { _ ! Messages.FileDeleted(file) }
                  }
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
      case e: Throwable => self ! Messages.Internal.WatchThreadFailed(e)
    }
  }

  override def preStart() = {
    //re-register observers 
    observers.keys foreach {path => self ! Messages.WatchPath(path)}
    //run watching thread
    executor.execute(new Runnable {
      def run = watchFolder
    })
  }

  override def postStop() = {
    executor.shutdownNow()
  }

}