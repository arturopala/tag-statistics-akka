package code.arturopala.tagstatisticsakka.fileswatch

import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }
import akka.actor.{ Actor, Props, ActorSystem }
import akka.testkit.{ ImplicitSender, TestKit, TestActorRef }
import scala.concurrent.duration._
import Messages._
import Messages.Internal._
import java.nio.file.{Path,FileSystems,Files}

class FilesWatchActorSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll {
  
  val testPath = FileSystems.getDefault.getPath("target/testdata")
  val testPath2 = testPath.resolve("2")

  def this() = this(ActorSystem("FilesWatchActorSpec"))
  
  override def beforeAll: Unit = {
    Files.createDirectories(testPath)
    Files.createDirectories(testPath2)
  }

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }
  
  "A FilesWatchService" should "be able to register new watch of a directory path" in {
    val fileSystem = testPath.getFileSystem
    val tested = TestActorRef(new FilesWatchService(fileSystem.newWatchService(), Map()))
    tested ! WatchPath(testPath)
    expectMsg(WatchPathAck(testPath))
    tested.underlyingActor.watchKey2PathMap should contain value (testPath)
    tested.underlyingActor.observers should not be (null)
  }
  
  it should "be able to refresh map of observers (actors)" in {
    val fileSystem = testPath.getFileSystem
    val tested = TestActorRef(new FilesWatchService(fileSystem.newWatchService(), Map()))
    val dummyRef1, dummyRef2, dummyRef3 = _system.actorOf(Props.empty)
    tested ! RefreshObservers(Map(testPath -> Set(dummyRef1,dummyRef2,dummyRef3)))
    tested.underlyingActor.observers should contain key (testPath)
    tested.underlyingActor.observers(testPath) should contain (dummyRef1)
    tested.underlyingActor.observers(testPath) should contain (dummyRef2)
    tested.underlyingActor.observers(testPath) should contain (dummyRef3)
  }
  
  it should "be able to unregister previously watched path" in {
    val fileSystem = testPath.getFileSystem
    val tested = TestActorRef(new FilesWatchService(fileSystem.newWatchService(), Map()))
    tested ! WatchPath(testPath)
    expectMsg(WatchPathAck(testPath))
    tested.underlyingActor.watchKey2PathMap should contain value (testPath)
    tested.underlyingActor.observers should not be (null)
    tested ! UnwatchPath(testPath)
    expectMsg(UnwatchPathAck(testPath))
    tested.underlyingActor.watchKey2PathMap should be ('empty)
    tested.underlyingActor.observers should not be (null)
  }
  
  it should "generate file creation event for newly created file" in {
    val fileSystem = testPath.getFileSystem
    val tested = TestActorRef(new FilesWatchService(fileSystem.newWatchService(), Map()))
    

    tested ! WatchPath(testPath)
    tested.underlyingActor.watchKey2PathMap should contain value (testPath)
    tested.underlyingActor.observers should not be (null)
    
    val testFilePath = Files.createTempFile(testPath,"test","")

    expectMsg(WatchPathAck(testPath))
    
  }
  
  "A FilesWatchActorWorker" should "be able to register new watch of a directory path" in {
    val fileSystem = testPath.getFileSystem
    val tested = TestActorRef(new FilesWatchActorWorker(fileSystem))
    tested ! WatchPath(testPath)
    tested.underlyingActor.observers should contain key (testPath)
    tested.underlyingActor.watcher should not be (null)
  }
  
  it should "be able to unregister previously watched path and terminate" in {
    val fileSystem = testPath.getFileSystem
    val tested = TestActorRef(new FilesWatchActorWorker(fileSystem))
    tested ! WatchPath(testPath)
    tested.underlyingActor.observers should contain key (testPath)
    tested ! UnwatchPath(testPath)
    tested should be a 'isTerminated
  }
  
  it should "be able to unregister previously watched path and stay alive" in {
    val fileSystem = testPath.getFileSystem
    val tested = TestActorRef(new FilesWatchActorWorker(fileSystem))
    tested ! WatchPath(testPath)
    tested ! WatchPath(testPath2)
    tested.underlyingActor.observers should contain key (testPath)
    tested.underlyingActor.observers should contain key (testPath2)
    tested ! UnwatchPath(testPath)
    tested.underlyingActor.observers should contain key (testPath2)
    tested should not be 'isTerminated
  }

  "A FilesWatchActor" should "be able to register new watch of a directory path" in {
    val tested = TestActorRef(new FilesWatchActor)
    tested ! WatchPath(testPath)
    tested.underlyingActor.workerMap should contain key (testPath.getFileSystem())
  }
  
}