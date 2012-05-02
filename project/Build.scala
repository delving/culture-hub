import sbt._
import PlayProject._
import sbt.Keys._
import scala._

object ApplicationBuild extends Build {

  val appName = "culture-hub"
  val appVersion = "1.0"

  val dosVersion = "1.5"

  val coreVersion = "1.0-SNAPSHOT"

  val delvingReleases = "Delving Releases Repository" at "http://development.delving.org:8081/nexus/content/groups/public"
  val delvingSnapshots = "Delving Snapshot Repository" at "http://development.delving.org:8081/nexus/content/repositories/snapshots"
  def delvingRepository(version: String) = if (version.endsWith("SNAPSHOT")) delvingSnapshots else delvingReleases

  val commonResolvers = Seq(
    "sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
    "sonatype releases" at "https://oss.sonatype.org/content/repositories/releases/",
    delvingSnapshots,
    delvingReleases

  )

  val appDependencies = Seq(
    "org.apache.amber"          %  "oauth2-authzserver"              % "0.2-SNAPSHOT",
    "org.apache.amber"          %  "oauth2-client"                   % "0.2-SNAPSHOT",
    "net.liftweb"               %% "lift-json-ext"                   % "2.4-M4"
  )

  val coreDependencies = Seq(
    "eu.delving"                %% "play2-extensions"                 % "1.0-SNAPSHOT",

    "eu.delving"                %  "definitions"                     % "1.0-SNAPSHOT"      changing(),
    "eu.delving"                %  "sip-core"                        % "1.0.5-SNAPSHOT",

    "commons-validator"         %  "commons-validator"               % "1.3.1",
    "org.apache.solr"           %  "solr-solrj"                      % "3.4.0",
    "org.apache.tika"           %  "tika-parsers"                    % "1.0"
  )

  val core = PlayProject("culturehub-core", coreVersion, coreDependencies, file("core/")).settings(
    organization := "eu.delving",
    version := coreVersion,
    publishTo := Some(delvingRepository(coreVersion)),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    publishMavenStyle := true,
    resolvers ++= commonResolvers

  )

  val dosDependencies = Seq(
    "eu.delving"                %% "play2-extensions"                 % "1.0-SNAPSHOT",
    "com.thebuzzmedia"          %  "imgscalr-lib"                     % "3.2"
  )

  val dos = PlayProject("dos", dosVersion, dosDependencies, path = file("modules/dos")).settings(
    resolvers += "buzzmedia" at "http://maven.thebuzzmedia.com",
    resolvers ++= commonResolvers
  )

  // for later
  val musipDependencies = Seq(
    "eu.delving"               %% "culturehub-core"                   % "1.0-SNAPSHOT"
  )

  // TODO move to its own source repo once we have something stable
  val musip = PlayProject("musip", "1.0-SNAPSHOT", Seq.empty, path = file("modules/musip")).settings(
    resolvers ++= commonResolvers
  ).dependsOn(core)

  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(

    resolvers += Resolver.file("local-ivy-repo", file(Path.userHome + "/.ivy2/local"))(Resolver.ivyStylePatterns),
    resolvers ++= commonResolvers,
    resolvers += "apache-snapshots" at "https://repository.apache.org/content/groups/snapshots-group/",

    routesImport += "extensions.Binders._"

  ).dependsOn(core, dos, musip)

}
