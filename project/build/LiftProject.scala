import sbt._

class LiftProject(info: ProjectInfo) extends DefaultWebProject(info) {

  // Add Maven Local repository for SBT to search for (disable if this doesn't suit you)
  val mavenLocal = "Local Maven Repository" at "file://"+Path.userHome+"/.m2/repository"

  
}
