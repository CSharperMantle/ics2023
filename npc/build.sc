// import Mill dependency
import mill._
import mill.define.Sources
import mill.modules.Util
import mill.scalalib.scalafmt.ScalafmtModule
import mill.scalalib.TestModule.ScalaTest
import mill.scalalib._
// support BSP
import mill.bsp._

object playground extends SbtModule with ScalafmtModule { m =>
  override def millSourcePath = os.pwd / "src"
  override def scalaVersion   = "2.13.12"
  override def scalacOptions = Seq(
    "-language:reflectiveCalls",
    "-deprecation",
    "-feature",
    "-Xcheckinit"
  )
  override def sources = T.sources {
    super.sources() ++ Seq(PathRef(millSourcePath / "main"))
  }
  override def ivyDeps = Agg(
    ivy"org.chipsalliance::chisel:7.0.0-M2"
  )
  override def scalacPluginIvyDeps = Agg(
    ivy"org.chipsalliance:::chisel-plugin:7.0.0-M2"
  )

  object test extends SbtModuleTests with TestModule.ScalaTest with ScalafmtModule {
    override def sources = T.sources {
      super.sources() ++ Seq(PathRef(millSourcePath / "test"))
    }
    override def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"org.scalatest::scalatest:3.2.19"
    )
  }

  def repositoriesTask = T.task {
    Seq(
      coursier.MavenRepository("https://repo.scala-sbt.org/scalasbt/maven-releases"),
      coursier.MavenRepository("https://oss.sonatype.org/content/repositories/releases"),
      coursier.MavenRepository("https://oss.sonatype.org/content/repositories/snapshots")
    ) ++ super.repositoriesTask()
  }
}
