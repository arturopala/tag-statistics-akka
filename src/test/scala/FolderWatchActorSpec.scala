package code.arturopala.tagstatisticsakka.fileswatch

import java.nio.charset.Charset
import java.nio.file.{ FileSystems, Files, Path }
import java.util.function.Consumer
import org.junit.runner.RunWith
import org.scalatest.{ Finders, FlatSpecLike, Matchers }
import FolderWatchActorMessages.{ UnwatchPath, UnwatchPathAck, WatchPath, WatchPathAck, FileCreated }
import FolderWatchActorMessages.Internal.RefreshObservers
import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit }
import org.scalatest.junit.JUnitRunner
import com.typesafe.config.ConfigFactory
import akka.testkit.TestProbe

@RunWith(classOf[JUnitRunner])
class FolderWatchActorSpec extends FlatSpecLike with Matchers {

  val config = """
  |akka.log-dead-letters = 0
  |akka.log-dead-letters-during-shutdown = off
    """.stripMargin

  val testFolder = Files.createDirectories(FileSystems.getDefault.getPath("target/testoutput"))
  val actorSystemConfig = ConfigFactory.parseString(config).withFallback(ConfigFactory.load)

  class ActorsTest extends TestKit(ActorSystem("test", actorSystemConfig)) with ImplicitSender {

    val testPath = Files.createTempDirectory(testFolder, "test")
    val dummyActorRef = system.actorOf(Props.empty)

    def createTestFile(folder: Path, fileName: String): Path = {
      val testFilePath = Files.createTempFile(folder, fileName, ".test")
      val w = Files.newBufferedWriter(testFilePath, Charset.forName("utf-8"))
      w.write("test file should be deleted afterward")
      w.flush()
      testFilePath
    }

    def clean(implicit system: ActorSystem): Unit = {
      Thread.sleep(100)
      TestKit.shutdownActorSystem(system)
      Files.list(testPath).forEach(new Consumer[Path]() { def accept(path: Path): Unit = { Files.delete(path) } })
      Files.delete(testPath)
    }
  }

  "A FolderWatchService" should "be able to register new watch of a directory path" in new ActorsTest {
    val fileSystem = testPath.getFileSystem
    val tested = TestActorRef(new FolderWatchService(fileSystem.newWatchService(), Map()))
    tested ! WatchPath(testPath, dummyActorRef)
    expectMsg(WatchPathAck(testPath))
    tested.underlyingActor.watchKey2PathMap should contain value testPath
    tested.underlyingActor.observers should not be null
    clean
  }

  it should "be able to refresh map of observers (actors)" in new ActorsTest {
    val fileSystem = testPath.getFileSystem
    val tested = TestActorRef(new FolderWatchService(fileSystem.newWatchService(), Map()))
    val dummyActorRef2, dummyActorRef3 = system.actorOf(Props.empty)
    tested ! RefreshObservers(Map(testPath -> Set(dummyActorRef, dummyActorRef2, dummyActorRef3)))
    tested.underlyingActor.observers should contain key testPath
    tested.underlyingActor.observers(testPath) should contain(dummyActorRef)
    tested.underlyingActor.observers(testPath) should contain(dummyActorRef2)
    tested.underlyingActor.observers(testPath) should contain(dummyActorRef3)
    clean
  }

  it should "be able to unregister previously watched path" in new ActorsTest {
    val fileSystem = testPath.getFileSystem
    val tested = TestActorRef(new FolderWatchService(fileSystem.newWatchService(), Map()))
    tested ! WatchPath(testPath, dummyActorRef)
    expectMsg(WatchPathAck(testPath))
    tested.underlyingActor.watchKey2PathMap should contain value testPath
    tested.underlyingActor.observers should not be null
    tested ! UnwatchPath(testPath, dummyActorRef)
    expectMsg(UnwatchPathAck(testPath))
    tested.underlyingActor.watchKey2PathMap should be('empty)
    tested.underlyingActor.observers should not be null
    clean
  }
  "A FolderWatchActorWorker" should "be able to register new watch of a directory path" in new ActorsTest {
    val fileSystem = testPath.getFileSystem
    val tested = TestActorRef(new FolderWatchActorWorker(fileSystem))
    tested ! WatchPath(testPath, dummyActorRef)
    expectMsg(WatchPathAck(testPath))
    tested.underlyingActor.observers should contain key testPath
    tested.underlyingActor.watcher should not be null
    clean
  }

  it should "be able to unregister previously watched path and terminate itself" in new ActorsTest {
    val fileSystem = testPath.getFileSystem
    val tested = TestActorRef(new FolderWatchActorWorker(fileSystem))
    tested ! WatchPath(testPath, dummyActorRef)
    expectMsg(WatchPathAck(testPath))
    tested ! UnwatchPath(testPath, dummyActorRef)
    expectMsg(UnwatchPathAck(testPath))
    Thread.sleep(200)
    tested should be('isTerminated)
    clean
  }

  it should "be able to unregister previously watched path and stay alive" in new ActorsTest {
    val testPath2 = testPath.resolve("2")
    Files.createDirectories(testPath2)
    val fileSystem = testPath.getFileSystem
    val tested = TestActorRef(new FolderWatchActorWorker(fileSystem))
    tested ! WatchPath(testPath, dummyActorRef)
    expectMsg(WatchPathAck(testPath))
    tested ! WatchPath(testPath2, dummyActorRef)
    expectMsg(WatchPathAck(testPath2))
    tested.underlyingActor.observers should contain key testPath
    tested.underlyingActor.observers should contain key testPath2
    tested ! UnwatchPath(testPath, dummyActorRef)
    expectMsg(UnwatchPathAck(testPath))
    tested.underlyingActor.observers should contain key testPath2
    tested should not be 'isTerminated
    Files.delete(testPath2)
    clean
  }

  "A FolderWatchActor" should "be able to register new watch of a directory path" in new ActorsTest {
    val tested = TestActorRef(new FolderWatchActor)
    tested ! WatchPath(testPath, dummyActorRef)
    tested.underlyingActor.workerMap should contain key testPath.getFileSystem
    expectMsg(WatchPathAck(testPath))
  }

  it should "be able to de-register previous watch of a directory path" in new ActorsTest {
    val tested = TestActorRef(new FolderWatchActor)
    tested ! WatchPath(testPath, dummyActorRef)
    expectMsg(WatchPathAck(testPath))
    tested ! UnwatchPath(testPath, dummyActorRef)
    expectMsg(UnwatchPathAck(testPath))
    clean
  }

  it should "be able to watch file creation" in new ActorsTest {
    val tested = TestActorRef(new FolderWatchActor)
    val probe = TestProbe()
    tested ! WatchPath(testPath, probe.ref)
    expectMsg(WatchPathAck(testPath))
    val testFilePath = createTestFile(testPath, "test")
    probe.expectMsg(FileCreated(testFilePath))
    Files.delete(testFilePath)
    clean
  }

}
