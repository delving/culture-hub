import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

  val appName = "culture-hub"
  val appVersion = "1.0"

  val dosVersion = "1.5"

  val coreVersion = "1.0-SNAPSHOT"

  val delvingReleases = "Delving Releases Repository" at "http://development.delving.org:8081/nexus/content/groups/public"
  val delvingSnapshots = "Delving Snapshot Repository" at "http://development.delving.org:8081/nexus/content/repositories/snapshots"
  def delvingRepository(version: String) = if (version.endsWith("SNAPSHOT")) delvingSnapshots else delvingReleases

  val appDependencies = Seq(
    "org.apache.amber"          %  "oauth2-authzserver"              % "0.2-SNAPSHOT",
    "org.apache.amber"          %  "oauth2-client"                   % "0.2-SNAPSHOT",
    "net.liftweb"               %% "lift-json-ext"                   % "2.4-M4",

    "eu.delving"                %  "definitions"                     % "1.0-SNAPSHOT"      changing(),
    "eu.delving"                %  "sip-core"                        % "1.0.3-SNAPSHOT",
    "eu.delving"                %  "sip-creator"                     % "1.0.3-SNAPSHOT",
    "eu.delving"                %% "play2-extensions"                % "1.0-SNAPSHOT",

    "org.apache.solr"           %  "solr-solrj"                      % "3.4.0",
    "org.apache.tika"           %  "tika-parsers"                    % "1.0"

  )

  val coreDependencies = Seq(
    "eu.delving"                %% "play2-extensions"                 % "1.0-SNAPSHOT"
  )

  val core = Project("culturehub-core", file("core/"), Seq.empty).settings(
    libraryDependencies := coreDependencies,
    organization := "eu.delving",
    version := coreVersion,
    resolvers += delvingSnapshots,
    resolvers += delvingReleases,
    resolvers +="repo.novus rels" at "http://repo.novus.com/releases/",
    resolvers += "repo.novus snaps" at "http://repo.novus.com/snapshots/",
    publishTo := Some(delvingRepository(coreVersion)),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    publishMavenStyle := true
  )

  val dosDependencies = Seq(
    "eu.delving"                %% "play2-extensions"                 % "1.0-SNAPSHOT",
    "com.thebuzzmedia"          %  "imgscalr-lib"                     % "3.2"
  )

  val dos = PlayProject("dos", dosVersion, dosDependencies, path = file("modules/dos")).settings(
    resolvers += "buzzmedia" at "http://maven.thebuzzmedia.com",
    resolvers += delvingSnapshots,
    resolvers += delvingReleases
  )

  // for later
  val musipDependencies = Seq(
    "eu.delving"               %% "culturehub-core"                   % "1.0-SNAPSHOT"
  )

  // TODO move to its own source repo once we have something stable
  val musip = PlayProject("musip", "1.0-SNAPSHOT", Seq.empty, path = file("modules/musip")).settings(
    resolvers += delvingSnapshots,
    resolvers += delvingReleases
  ).dependsOn(core)

  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(

    resolvers += Resolver.file("local-ivy-repo", file(Path.userHome + "/.ivy2/local"))(Resolver.ivyStylePatterns),
    resolvers += "jahia" at "http://maven.jahia.org/maven2",
    resolvers += "apache-snapshots" at "https://repository.apache.org/content/groups/snapshots-group/",
    resolvers += delvingSnapshots,
    resolvers += delvingReleases,

    routesImport += "extensions.Binders._"

  ).dependsOn(core, dos, musip)

}
