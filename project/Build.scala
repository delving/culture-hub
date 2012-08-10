import sbt._
import scala._
import PlayProject._
import sbt.Keys._
import sbtbuildinfo.Plugin._
import eu.delving.templates.Plugin._

object Build extends sbt.Build {

  val sipCreator = SettingKey[String]("sip-creator", "Version of the Sip-Creator")

  val appName = "culture-hub"
  val cultureHubVersion = "12.08-SNAPSHOT"
  val sipCreatorVersion = "1.0.7-SNAPSHOT"
  val playExtensionsVersion = "1.3.1"

  val dosVersion = "1.5"

  val webCoreVersion = "1.0-SNAPSHOT"

  val delvingReleases = "Delving Releases Repository" at "http://development.delving.org:8081/nexus/content/repositories/releases"
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
    "net.liftweb"               %% "lift-json-ext"                   % "2.4-M4",
    "eu.delving"                %% "themes"                          % "1.0-SNAPSHOT"      changing()
  )

  val webCoreDependencies = Seq(
    "eu.delving"                %% "play2-extensions"                % playExtensionsVersion,

    "eu.delving"                %  "definitions"                     % "1.0-SNAPSHOT"      changing(),
    "eu.delving"                %  "sip-core"                        % sipCreatorVersion,
    "eu.delving"                %% "basex-scala-client"              % "0.1-SNAPSHOT",

    "org.apache.solr"           %  "solr-solrj"                      % "3.6.0",
    "org.apache.httpcomponents" %  "httpclient"                      % "4.1.2",
    "org.apache.httpcomponents" %  "httpmime"                        % "4.1.2",

    "org.apache.tika"           %  "tika-parsers"                    % "1.0",

    "org.scalesxml"             %% "scales-xml"                      % "0.3-RC6"
  )

  val webCore = PlayProject("web-core", webCoreVersion, webCoreDependencies, file("web-core/")).settings(
    organization := "eu.delving",
    version := webCoreVersion,
    publishTo := Some(delvingRepository(webCoreVersion)),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    publishMavenStyle := true,
    resolvers ++= commonResolvers,
    resolvers += "BaseX Repository" at "http://files.basex.org/maven"
  )

  val dosDependencies = Seq(
    "eu.delving"                %% "play2-extensions"                 % playExtensionsVersion,
    "com.thebuzzmedia"          %  "imgscalr-lib"                     % "3.2"
  )

  val dos = PlayProject("dos", dosVersion, dosDependencies, path = file("modules/dos")).settings(
    resolvers += "buzzmedia" at "http://maven.thebuzzmedia.com",
    resolvers ++= commonResolvers
  ).dependsOn(webCore % "test->test;compile->compile")

  // TODO move to its own source repo once we have something stable
  val musip = PlayProject("musip", "1.0-SNAPSHOT", Seq.empty, path = file("modules/musip")).settings(
    resolvers ++= commonResolvers
  ).dependsOn(webCore % "test->test;compile->compile")

  val itin = PlayProject("itin", "1.0", Seq.empty, path = file("modules/itin")).settings(
    resolvers ++= commonResolvers
  ).dependsOn(webCore % "test->test;compile->compile")

  val dataSet = PlayProject("dataset", "1.0-SNAPSHOT", Seq.empty, path = file("modules/dataset"), settings = Defaults.defaultSettings ++ buildInfoSettings).settings(
    resolvers ++= commonResolvers,
    sipCreator := sipCreatorVersion,
    sourceGenerators in Compile <+= buildInfo,
    buildInfoKeys := Seq[Scoped](name, version, scalaVersion, sbtVersion, sipCreator),
    buildInfoPackage := "eu.delving.culturehub"
  ).dependsOn(webCore % "test->test;compile->compile")


  val advancedSearch = PlayProject("advanced-search", "1.0-SNAPSHOT", Seq.empty, path = file("modules/advanced-search")).settings(
    resolvers ++= commonResolvers
  ).dependsOn(webCore % "test->test;compile->compile")

  val statistics = PlayProject("statistics", "1.0-SNAPSHOT", Seq.empty, path = file("modules/statistics")).settings(
    resolvers ++= commonResolvers
  ).dependsOn(webCore, dataSet) // FIXME

  val customHarvestCollection = PlayProject("custom-harvest-collection", "1.0", Seq.empty, path = file("modules/custom-harvest-collection")).settings(
    resolvers ++= commonResolvers
  ).dependsOn(webCore % "test->test;compile->compile")

  val main = PlayProject(appName, cultureHubVersion, appDependencies, mainLang = SCALA, settings = Defaults.defaultSettings ++ buildInfoSettings ++ groovyTemplatesSettings).settings(

    onLoadMessage := "May the force be with you",

    resolvers += Resolver.file("local-ivy-repo", file(Path.userHome + "/.ivy2/local"))(Resolver.ivyStylePatterns),
    resolvers ++= commonResolvers,
    resolvers += "apache-snapshots" at "https://repository.apache.org/content/groups/snapshots-group/",

    sourceGenerators in Compile <+= groovyTemplatesList,

    sipCreator := sipCreatorVersion,
    sourceGenerators in Compile <+= buildInfo,
    buildInfoKeys := Seq[Scoped](name, version, scalaVersion, sbtVersion, sipCreator),
    buildInfoPackage := "eu.delving.culturehub",

    watchSources <++= baseDirectory.map { path => ((path / "web-core"                               / "app") ** "*").get },
    watchSources <++= baseDirectory.map { path => ((path / "modules"  / "custom-harvest-collection" / "app") ** "*").get },
    watchSources <++= baseDirectory.map { path => ((path / "modules"  / "advanced-search"           / "app") ** "*").get },
    watchSources <++= baseDirectory.map { path => ((path / "modules"  / "statistics"                / "app") ** "*").get },
    watchSources <++= baseDirectory.map { path => ((path / "modules"  / "dos"                       / "app") ** "*").get },
    watchSources <++= baseDirectory.map { path => ((path / "modules"  / "musip"                     / "app") ** "*").get },

    publishTo := Some(delvingRepository(cultureHubVersion)),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),

    routesImport += "extensions.Binders._",

    parallelExecution in (ThisBuild) := false

  ).settings(
    addArtifact(
      Artifact((appName + "-" + cultureHubVersion), "zip", "zip"), dist
    ).settings :_*
  ).dependsOn(
    webCore                 % "test->test;compile->compile",
    dataSet                 % "test->test;compile->compile",
    dos                     % "test->test;compile->compile",
    statistics              % "test->test;compile->compile",
    musip                   % "test->test;compile->compile",
    customHarvestCollection % "test->test;compile->compile",
    itin                    % "test->test;compile->compile",
    advancedSearch          % "test->test;compile->compile"
  ).aggregate(
    webCore,
    dataSet,
    dos,
    statistics,
    musip,
    customHarvestCollection,
    itin,
    advancedSearch
  )


}
