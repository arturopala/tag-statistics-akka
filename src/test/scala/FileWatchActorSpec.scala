package org.encalmo.tagstatisticsakka

import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }
import akka.actor.{ Actor, Props, ActorSystem }
import akka.testkit.{ ImplicitSender, TestKit, TestActorRef }
import scala.concurrent.duration._

class FileWatchActorSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll {
  
  val testFolder = "target/testdata"

  def this() = this(ActorSystem("FileWatchActorSpec"))
  
  override def beforeAll: Unit = {
    new java.io.File(testFolder).mkdirs()
  }

  override def afterAll: Unit = {
    system.shutdown()
    system.awaitTermination(10.seconds)
  }

  "An FileWatchActor" should "be able to watch a new directory" in {
    val watcher = TestActorRef(Props[FileWatchActor])
    watcher ! WatchFolder(testFolder)
  }
  
}