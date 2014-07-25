package org.encalmo.tagstatisticsakka

import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }
import akka.actor.{ Actor, Props, ActorSystem }
import akka.testkit.{ ImplicitSender, TestKit, TestActorRef }
import scala.concurrent.duration._
import Messages._
import InternalMessages._
import java.nio.file.{Path,FileSystems}

class DirectoryWatchActorSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll {
  
  val testPath = FileSystems.getDefault().getPath("target/testdata")
  val testPath2 = testPath.resolve("2")

  def this() = this(ActorSystem("DirectoryWatchActorSpec"))
  
  override def beforeAll: Unit = {
    testPath.toFile().mkdirs()
    testPath2.toFile().mkdirs()
  }

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }
  
  "An WatchServiceWorker" should "be able to register watch of a directory path" in {
    val fileSystem = testPath.getFileSystem()
    val tested = TestActorRef(new WatchServiceWorker(fileSystem.newWatchService(), Map()))
    tested ! WatchPath(testPath)
    tested.underlyingActor.watchKey2PathMap should contain value (testPath)
    tested.underlyingActor.observers should not be (null)
  }
  
  it should "be able to refresh map of observers" in {
    val fileSystem = testPath.getFileSystem()
    val tested = TestActorRef(new WatchServiceWorker(fileSystem.newWatchService(), Map()))
    val dummyRef1, dummyRef2, dummyRef3 = _system.actorOf(Props.empty)
    tested ! RefreshObservers(Map(testPath -> Set(dummyRef1,dummyRef2,dummyRef3)))
    tested.underlyingActor.observers should contain key (testPath)
    tested.underlyingActor.observers(testPath) should contain (dummyRef1)
    tested.underlyingActor.observers(testPath) should contain (dummyRef2)
    tested.underlyingActor.observers(testPath) should contain (dummyRef3)
  }
  
  it should "be able to unregister previous watch" in {
    val fileSystem = testPath.getFileSystem()
    val tested = TestActorRef(new WatchServiceWorker(fileSystem.newWatchService(), Map()))
    tested ! WatchPath(testPath)
    tested.underlyingActor.watchKey2PathMap should contain value (testPath)
    tested.underlyingActor.observers should not be (null)
    tested ! UnwatchPath(testPath)
    tested.underlyingActor.watchKey2PathMap should be ('empty)
    tested.underlyingActor.observers should not be (null)
  }
  
  it should "generate file creation events" in {
    val fileSystem = testPath.getFileSystem()
    val tested = TestActorRef(new WatchServiceWorker(fileSystem.newWatchService(), Map()))

    tested ! WatchPath(testPath)
    tested.underlyingActor.watchKey2PathMap should contain value (testPath)
    tested.underlyingActor.observers should not be (null)
    
  }
  
  "An DirectoryWatchWorker" should "be able to register watch of a directory path" in {
    val fileSystem = testPath.getFileSystem()
    val tested = TestActorRef(new DirectoryWatchWorker(fileSystem))
    tested ! WatchPath(testPath)
    tested.underlyingActor.observers should contain key (testPath)
    tested.underlyingActor.worker should not be (null)
  }
  
  it should "be able to unregister previous single watch and terminate" in {
    val fileSystem = testPath.getFileSystem()
    val tested = TestActorRef(new DirectoryWatchWorker(fileSystem))
    tested ! WatchPath(testPath)
    tested.underlyingActor.observers should contain key (testPath)
    tested ! UnwatchPath(testPath)
    tested should be a 'isTerminated
  }
  
  it should "be able to unregister previous watch and stay live" in {
    val fileSystem = testPath.getFileSystem()
    val tested = TestActorRef(new DirectoryWatchWorker(fileSystem))
    tested ! WatchPath(testPath)
    tested ! WatchPath(testPath2)
    tested.underlyingActor.observers should contain key (testPath)
    tested.underlyingActor.observers should contain key (testPath2)
    tested ! UnwatchPath(testPath)
    tested.underlyingActor.observers should contain key (testPath2)
    tested should not be 'isTerminated
  }

  "An DirectoryWatchActor" should "be able to register watch of a directory path" in {
    val tested = TestActorRef(new DirectoryWatchActor)
    tested ! WatchPath(testPath)
    tested.underlyingActor.workerMap should contain key (testPath.getFileSystem())
  }
  
}