package example

import sbt.internal.inc._
import sbt.internal.util.{ConsoleAppender, ConsoleOut}
import sbt.util.LogExchange
import xsbti.{PathBasedFile, VirtualFile}
import xsbti.compile.{CompilerCache => _, FileAnalysisStore => _, ScalaInstance => _, _}

import java.io.File
import java.util.Optional
import java.nio.file.{Files, Paths}

object MainClass extends App {
  val appender = ConsoleAppender("test")
  val logger = LogExchange.logger("a")
  LogExchange.bindLoggerAppenders("a", (appender  -> sbt.util.Level.Info) :: Nil)
  val reporter = new ManagedLoggedReporter(10, logger)

  val lookup = new xsbti.compile.PerClasspathEntryLookup {
    def analysis(classpathEntry: VirtualFile): Optional[CompileAnalysis] = Optional.empty
    def definesClass(classpathEntry: VirtualFile): DefinesClass =
      Locate.definesClass(classpathEntry)
  }
  val zincFile = Files.createTempDirectory("zincCache")
  val classesDir = Files.createTempDirectory("zincClasses")
  val converter = PlainVirtualFileConverter.converter
  val classpath = List(classesDir).map(converter.toVirtualFile).toArray
  val virtualSources = List(Paths.get("src/main/resources/mill/main/client/FileToStreamTailer.java")).map(converter.toVirtualFile).toArray
  val javacOptions = List("-source", "1.8", "-target", "1.8", "-encoding", "UTF-8")
  val ic = new IncrementalCompilerImpl()
  val javaOnlyCompilers = {
    // Keep the classpath as written by the user
    val classpathOptions = ClasspathOptions.of(
      /*bootLibrary*/ false,
      /*compiler*/false,
      /*extra*/false,
      /*autoBoot*/false,
      /*filterLibrary*/false
    )

    val dummyFile = new File("")
    // Zinc does not have an entry point for Java-only compilation, so we need
    // to make up a dummy ScalaCompiler instance.
    val scalac = ZincUtil.scalaCompiler(
      new ScalaInstance("", null, null, null, new Array(0), new Array(0), new Array(0), Some("")),
      dummyFile,
      classpathOptions // this is used for javac too
    )

    ic.compilers(
      instance = null,
      classpathOptions,
      None,
      scalac
    )
  }
  val inputs = ic.inputs(
    classpath = classpath,
    sources = virtualSources,
    classesDirectory = classesDir,
    earlyJarPath = None,
    scalacOptions = List.empty.toArray,
    javacOptions = javacOptions.toArray,
    maxErrors = 10,
    sourcePositionMappers = Array(),
    order = CompileOrder.Mixed,
    compilers = javaOnlyCompilers,
    setup = ic.setup(
      lookup,
      skip = false,
      zincFile,
      new FreshCompilerCache,
      IncOptions.of(),
      reporter,
      None,
      None,
      Array()
    ),
    pr = ic.emptyPreviousResult,
    temporaryClassesDirectory = java.util.Optional.empty(),
    converter = converter,
    stampReader = Stamps.timeWrapBinaryStamps(converter)
  )

  ic.compile(in = inputs, logger = logger)
}
