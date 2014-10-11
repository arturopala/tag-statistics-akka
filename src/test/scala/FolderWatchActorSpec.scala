package code.arturopala.tagstatisticsakka.fileswatch

import java.nio.charset.Charset

import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }
import akka.actor.{ Actor, Props, ActorSystem }
import akka.testkit.{ ImplicitSender, TestKit, TestActorRef }
import scala.concurrent.duration._
import Messages._
import Messages.Internal._
import java.nio.file.{Path,FileSystems,Files}

class FolderWatchActorSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll {
  
  val testPath = FileSystems.getDefault.getPath("target/testdata")

  def this() = this(ActorSystem("FolderWatchActorSpec"))

  def createTestFile(folder:Path,fileName:String):Path = {
    val testFilePath = Files.createTempFile(folder,fileName,".test")
    val w = Files.newBufferedWriter(testFilePath, Charset.forName("utf-8"))
    w.write("test file should be deleted afterward")
    w.flush()
    testFilePath
  }
  
  override def beforeAll():Unit = {
    Files.createDirectories(testPath)
  }

  override def afterAll():Unit = {
    TestKit.shutdownActorSystem(system)
  }
  
  "A FolderWatchService" should "be able to register new watch of a directory path" in {
    val fileSystem = testPath.getFileSystem
    val tested = TestActorRef(new FolderWatchService(fileSystem.newWatchService(), Map()))
    tested ! WatchPath(testPath)
    expectMsg(WatchPathAck(testPath))
    tested.underlyingActor.watchKey2PathMap should contain value testPath
    tested.underlyingActor.observers should not be null
  }
  
  it should "be able to refresh map of observers (actors)" in {
    val fileSystem = testPath.getFileSystem
    val tested = TestActorRef(new FolderWatchService(fileSystem.newWatchService(), Map()))
    val dummyRef1, dummyRef2, dummyRef3 = _system.actorOf(Props.empty)
    tested ! RefreshObservers(Map(testPath -> Set(dummyRef1,dummyRef2,dummyRef3)))
    tested.underlyingActor.observers should contain key testPath
    tested.underlyingActor.observers(testPath) should contain (dummyRef1)
    tested.underlyingActor.observers(testPath) should contain (dummyRef2)
    tested.underlyingActor.observers(testPath) should contain (dummyRef3)
  }
  
  it should "be able to unregister previously watched path" in {
    val fileSystem = testPath.getFileSystem
    val tested = TestActorRef(new FolderWatchService(fileSystem.newWatchService(), Map()))
    tested ! WatchPath(testPath)
    expectMsg(WatchPathAck(testPath))
    tested.underlyingActor.watchKey2PathMap should contain value testPath
    tested.underlyingActor.observers should not be null
    tested ! UnwatchPath(testPath)
    expectMsg(UnwatchPathAck(testPath))
    tested.underlyingActor.watchKey2PathMap should be ('empty)
    tested.underlyingActor.observers should not be null
  }
  
  "A FolderWatchActorWorker" should "be able to register new watch of a directory path" in {
    val fileSystem = testPath.getFileSystem
    val tested = TestActorRef(new FolderWatchActorWorker(fileSystem))
    tested ! WatchPath(testPath)
    expectMsg(WatchPathAck(testPath))
    tested.underlyingActor.observers should contain key testPath
    tested.underlyingActor.watcher should not be null
  }
  
  it should "be able to unregister previously watched path and terminate itself" in {
    val fileSystem = testPath.getFileSystem
    val tested = TestActorRef(new FolderWatchActorWorker(fileSystem))
    tested ! WatchPath(testPath)
    expectMsg(WatchPathAck(testPath))
    tested ! UnwatchPath(testPath)
    expectMsg(UnwatchPathAck(testPath))
    Thread.sleep(200)
    tested should be ('isTerminated)
  }
  
  it should "be able to unregister previously watched path and stay alive" in {
    val testPath2 = testPath.resolve("2")
    Files.createDirectories(testPath2)
    val fileSystem = testPath.getFileSystem
    val tested = TestActorRef(new FolderWatchActorWorker(fileSystem))
    tested ! WatchPath(testPath)
    expectMsg(WatchPathAck(testPath))
    tested ! WatchPath(testPath2)
    expectMsg(WatchPathAck(testPath2))
    tested.underlyingActor.observers should contain key testPath
    tested.underlyingActor.observers should contain key testPath2
    tested ! UnwatchPath(testPath)
    expectMsg(UnwatchPathAck(testPath))
    tested.underlyingActor.observers should contain key testPath2
    tested should not be 'isTerminated
  }

  "A FolderWatchActor" should "be able to register new watch of a directory path" in {
    val tested = TestActorRef(new FolderWatchActor)
    tested ! WatchPath(testPath)
    tested.underlyingActor.workerMap should contain key testPath.getFileSystem
    expectMsg(WatchPathAck(testPath))
  }

  it should "be able to de-register previous watch of a directory path" in {
    val tested = TestActorRef(new FolderWatchActor)
    tested ! WatchPath(testPath)
    expectMsg(WatchPathAck(testPath))
    tested ! UnwatchPath(testPath)
    expectMsg(UnwatchPathAck(testPath))
  }

  it should "be able to watch file creation and deletion" in {
    val tested = TestActorRef(new FolderWatchActor)
    tested ! WatchPath(testPath)
    expectMsg(WatchPathAck(testPath))
    val testFilePath = createTestFile(testPath,"test")
    expectMsg(FileCreated(testFilePath))
    /*Thread.sleep(200)
    Files.delete(testFilePath)
    expectMsg(FileDeleted(testFilePath))*/
  }
  
}
