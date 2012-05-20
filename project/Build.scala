import sbt._
import PlayProject._
import sbt.Keys._
import scala._
import sbtbuildinfo.Plugin._

object ApplicationBuild extends Build {

  val sipCreator = SettingKey[String]("sip-creator", "Version of the Sip-Creator")

  val appName = "culture-hub"
  val cultureHubVersion = "1.0"
  val sipCreatorVersion = "1.0.5-SNAPSHOT"

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
    "eu.delving"                %  "sip-core"                        % sipCreatorVersion,

    "org.apache.solr"           %  "solr-solrj"                      % "3.6.0",
    "org.apache.httpcomponents" %  "httpclient"                      % "4.1.2",
    "org.apache.httpcomponents" %  "httpmime"                        % "4.1.2",

    "org.apache.tika"           %  "tika-parsers"                    % "1.0",

    "org.scalesxml"             %% "scales-xml"                      % "0.3-RC6"
  )

  val basexScalaClient = Project("basex-scala-client", file("modules/basex-scala-client")).settings(
    resolvers += "basex" at "http://files.basex.org/maven",
    libraryDependencies += "org.basex"  %     "basex" % "7.2.1",
    libraryDependencies += "org.specs2" %%   "specs2" % "1.7.1" %  "test"
  )

  val core = PlayProject("culturehub-core", coreVersion, coreDependencies, file("core/")).settings(
    organization := "eu.delving",
    version := coreVersion,
    publishTo := Some(delvingRepository(coreVersion)),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    publishMavenStyle := true,
    resolvers ++= commonResolvers
  ).dependsOn(basexScalaClient)

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


  val main = PlayProject(appName, cultureHubVersion, appDependencies, mainLang = SCALA, settings = Defaults.defaultSettings ++ buildInfoSettings).settings(

    onLoadMessage := "May the force be with you",

    sipCreator := sipCreatorVersion,
    resolvers += Resolver.file("local-ivy-repo", file(Path.userHome + "/.ivy2/local"))(Resolver.ivyStylePatterns),
    resolvers ++= commonResolvers,
    resolvers += "apache-snapshots" at "https://repository.apache.org/content/groups/snapshots-group/",

    sourceGenerators in Compile <+= buildInfo,

    buildInfoKeys := Seq[Scoped](name, version, scalaVersion, sbtVersion, sipCreator),

    buildInfoPackage := "eu.delving.culturehub",

    watchSources <++= baseDirectory map { path => ((path / "core" / "app") ** "*").get },

    watchSources <++= baseDirectory map { path => ((path / "modules" / "basex-scala-client") ** "*").get },

    routesImport += "extensions.Binders._"

  ).dependsOn(core, dos, musip)

}
