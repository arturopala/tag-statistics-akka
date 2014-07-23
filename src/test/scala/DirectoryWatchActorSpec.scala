package org.encalmo.tagstatisticsakka

import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }
import akka.actor.{ Actor, Props, ActorSystem }
import akka.testkit.{ ImplicitSender, TestKit, TestActorRef }
import scala.concurrent.duration._
import FileWatchMessages._
import java.nio.file.{Path,FileSystems}

class DirectoryWatchActorSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll {
  
  val testPath = FileSystems.getDefault().getPath("target/testdata")

  def this() = this(ActorSystem("DirectoryWatchActorSpec"))
  
  override def beforeAll: Unit = {
    testPath.toFile().mkdirs()
  }

  override def afterAll: Unit = {
    system.shutdown()
    system.awaitTermination(10.seconds)
  }

  "An DirectoryWatchActor" should "be able to watch a new directory" in {
    val watcher = TestActorRef(Props[DirectoryWatchActor])
    watcher ! WatchPath(testPath)
  }
  
}