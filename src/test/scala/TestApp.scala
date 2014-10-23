import java.io.Writer
import java.nio.charset.Charset
import java.nio.file.attribute.{ PosixFileAttributeView, PosixFileAttributes }
import java.nio.file.{ FileSystems, Paths, Path, Files }

import akka.actor.{ Actor, ActorLogging, Props, ActorSystem }
import code.arturopala.tagstatisticsakka.fileswatch.{ Messages, FolderWatchActor }

object TestApp {

  def main(args: Array[String]): Unit = {
    require(args(0) != null)
    val actorSystem = ActorSystem("testapp")
    val mainActor = actorSystem.actorOf(Props(classOf[MainActor]), "main")
    val path = FileSystems.getDefault.getPath(args(0))
    mainActor ! path
  }

}

class MainActor extends Actor with ActorLogging {

  val folderWatchActor = context.actorOf(Props(classOf[FolderWatchActor]), "folderWatch")

  def receive = {
    case path: Path => {
      folderWatchActor ! Messages.WatchPath(path)
    }
    case Messages.WatchPathAck(path) => {
      /*val file = Files.createFile(path.resolve("test.txt"))
      val w = Files.newBufferedWriter(file, Charset.forName("utf-8"))
      w.write("sdsdsadkjsj jsd kasjdjas djjaskjdwquuiidui sajdcm,nxmncmnmsancjsi djsdjisdij djksajkdj")
      w.flush()
      w.close()
      Thread.sleep(100)
      Files.delete(file)
      Thread.sleep(100)
      folderWatchActor ! Messages.UnwatchPath(path)*/
    }
    case Messages.UnwatchPathAck(path) => {

    }
    case Messages.FileCreated(path) => {

    }
    case Messages.FileDeleted(path) => {

    }
    case Messages.FileModified(path) => {

    }
  }

}
