import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "dos"
    val appVersion      = "1.0"

    val appDependencies = Seq(
      "com.thebuzzmedia"    %   "imgscalr-lib" % "3.2"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
    repositories += ("buzzmedia" at "http://maven.thebuzzmedia.com")
    )

}
