import java.nio.file.{ FileSystems, Path }
import akka.actor.{ Actor, ActorLogging, Props, ActorSystem }
import code.arturopala.tagstatisticsakka.fileswatch.FolderWatchActor
import code.arturopala.tagstatisticsakka.fileswatch.FolderWatchActorMessages._
import com.typesafe.config.ConfigFactory
import akka.actor.actorRef2Scala
import code.arturopala.tagstatisticsakka.fileswatch.FolderWatchActor

object SimpleApp {

  def main(args: Array[String]): Unit = {
    val actorSystem = ActorSystem("testapp")

    val config = ConfigFactory.load()
    require(config.hasPath("testapp.pathToWatch"))

    val mainActor = actorSystem.actorOf(Props(classOf[MainActor]), "main")
    val path = FileSystems.getDefault.getPath(config.getString("testapp.pathToWatch"))

    mainActor ! path

    Runtime.getRuntime().addShutdownHook(new Thread {
      override def run = {
        actorSystem.shutdown()
      }
    })
    Thread.sleep(0)
  }

}

class MainActor extends Actor with ActorLogging {

  val folderWatchActor = context.actorOf(Props(classOf[FolderWatchActor]), "folderWatch")
  val targetActor = context.actorOf(Props(classOf[ReceivingActor]))

  def receive = {
    case path: Path => {
      folderWatchActor ! WatchPath(path, targetActor)
    }
    case WatchPathAck(path) => {
      println(s"Watching path: $path")
    }
    case WatchPathNotValid(path) => {
      Console.err.println(s"Path not found: $path")
      System.exit(1)
    }
  }

}

class ReceivingActor extends Actor {
  def receive = {
    case FileCreated(path) => {
      println(s"Created: $path")
    }
    case FileDeleted(path) => {
      println(s"Deleted: $path")
    }
    case FileModified(path) => {
      println(s"Modified: $path")
    }
  }
}
