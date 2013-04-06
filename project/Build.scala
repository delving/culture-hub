import sbt._
import scala._
import play.Project._
import sbt.Keys._
import com.typesafe.sbt._
import sbtbuildinfo.Plugin._
import eu.delving.templates.Plugin._
import scala.Some

object Build extends sbt.Build {

  val cultureHub = SettingKey[String]("culture-hub", "Version of the CultureHub")
  val sipApp     = SettingKey[String]("sip-app", "Version of the SIP-App")
  val sipCore    = SettingKey[String]("sip-core", "Version of the SIP-Core")
  val schemaRepo = SettingKey[String]("schema-repo", "Version of the Schema Repository")

  val cultureHubPath = ""

  val appName = "culture-hub"
  val cultureHubVersion = "13.03"
  val sipAppVersion = "1.1.3"
  val sipCoreVersion = "1.1.3"
  val schemaRepoVersion = "1.1.3"
  val playExtensionsVersion = "1.4-SNAPSHOT"

  val dosVersion = "1.5"

  val webCoreVersion = "1.0-SNAPSHOT"

  val buildScalaVersion = "2.10.0"

  val delvingReleases = "Delving Releases Repository" at "http://nexus.delving.org/nexus/content/repositories/releases"
  val delvingSnapshots = "Delving Snapshot Repository" at "http://nexus.delving.org/nexus/content/repositories/snapshots"

  def delvingRepository(version: String) = if (version.endsWith("SNAPSHOT")) delvingSnapshots else delvingReleases

  val commonResolvers = Seq(
    "Delving Proxy repository" at "http://nexus.delving.org/nexus/content/groups/public/"
  )

  val appDependencies = Seq(
    "org.apache.amber"          %  "amber-oauth2-authzserver"        % "0.22-incubating",
    "org.apache.amber"          %  "amber-oauth2-client"             % "0.22-incubating",
    "eu.delving"                %  "themes"                          % "1.0-SNAPSHOT"      changing()
  )

  val webCoreDependencies = Seq(
    "eu.delving"                %% "play2-extensions"                % playExtensionsVersion,

    "eu.delving"                %  "definitions"                     % "1.0",
    "eu.delving"                %  "sip-core"                        % sipCoreVersion,
    "eu.delving"                %  "schema-repo"                     % schemaRepoVersion,
    "eu.delving"                %% "basex-scala-client"              % "0.6.1",

    "com.escalatesoft.subcut"   %% "subcut"                          % "2.0-SNAPSHOT" exclude ("org.scalatest", "scalatest"),
    "com.yammer.metrics"        %  "metrics-core"                    % "2.2.0",
    "nl.grons"                  %% "metrics-scala"                   % "2.2.0",

    "org.apache.httpcomponents" %  "httpclient"                      % "4.1.2",
    "org.apache.httpcomponents" %  "httpmime"                        % "4.1.2",

    "org.scalesxml"             %% "scales-xml"                      % "0.4.4",

    "org.scalatest"             %% "scalatest"                       % "2.0.M5b"             % "test",

    // temporary until https://play.lighthouseapp.com/projects/82401-play-20/tickets/970-xpathselecttext-regression is fixed
    "org.apache.ws.commons"             %    "ws-commons-util"          %   "1.0.1" exclude("junit", "junit")

  )


  val scalarifromSettings = SbtScalariform.scalariformSettings

  val webCore = play.Project("web-core", webCoreVersion, webCoreDependencies, file(cultureHubPath + "web-core/"), settings = Defaults.defaultSettings ++ buildInfoSettings).settings(
    organization := "eu.delving",
    version := webCoreVersion,
    publishTo := Some(delvingRepository(webCoreVersion)),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    publishMavenStyle := true,
    resolvers ++= commonResolvers,
    publish := { },
    testOptions in Test := Nil, // Required to use scalatest.
    cultureHub := cultureHubVersion,
    sipApp := sipAppVersion,
    sipCore := sipCoreVersion,
    schemaRepo := schemaRepoVersion,
    sourceGenerators in Compile <+= buildInfo,
    sourceGenerators in Test <+= buildInfo,
    buildInfoKeys := Seq(name, cultureHub, scalaVersion, sbtVersion, sipApp, sipCore, schemaRepo),
    buildInfoPackage := "eu.delving.culturehub",
    scalaVersion in (ThisBuild) := buildScalaVersion
  ).settings(scalarifromSettings :_*)


  val dosDependencies = Seq(
    "eu.delving"                %% "play2-extensions"                 % playExtensionsVersion,
    "org.imgscalr"               %  "imgscalr-lib"                    % "4.2"
  )

  val dos = play.Project("dos", dosVersion, dosDependencies, path = file(cultureHubPath + "modules/dos")).settings(
    resolvers ++= commonResolvers,
    publish := { }
  ).dependsOn(webCore % "test->test;compile->compile").settings(scalarifromSettings :_*)

  val thumbnail = play.Project("thumbnail", "1.0-SNAPSHOT", Seq.empty, path = file(cultureHubPath + "modules/thumbnail")).settings(
    resolvers ++= commonResolvers,
    publish := { }
  ).dependsOn(webCore % "test->test;compile->compile", dos).settings(scalarifromSettings :_*)

  val deepZoom = play.Project("deepZoom", "1.0-SNAPSHOT", Seq.empty, path = file(cultureHubPath + "modules/deepZoom")).settings(
    resolvers ++= commonResolvers,
    publish := { }
  ).dependsOn(webCore % "test->test;compile->compile", dos).settings(scalarifromSettings :_*)

  val hubNode = play.Project("hubNode", "1.0-SNAPSHOT", Seq.empty, path = file(cultureHubPath + "modules/hubNode")).settings(
    resolvers ++= commonResolvers,
    publish := {}
  ).dependsOn(webCore % "test->test;compile->compile").settings(scalarifromSettings :_*)

  val search = play.Project("search", "1.0-SNAPSHOT", Seq.empty, path = file(cultureHubPath + "modules/search")).settings(
    resolvers ++= commonResolvers,
    libraryDependencies ++= Seq(
      "org.apache.solr"           %  "solr-solrj"                      % "3.6.0",
      "org.apache.tika"           %  "tika-parsers"                    % "1.2"
    ),
    publish := {}
  ).dependsOn(webCore % "test->test;compile->compile").settings(scalarifromSettings :_*)

  val dataSet = play.Project("dataset", "1.0-SNAPSHOT", Seq.empty, path = file(cultureHubPath + "modules/dataset"), settings = Defaults.defaultSettings ++ buildInfoSettings).settings(
    libraryDependencies += "eu.delving" % "sip-core" % sipCoreVersion,
    resolvers ++= commonResolvers,
    sipApp := sipAppVersion,
    sipCore := sipCoreVersion,
    schemaRepo := schemaRepoVersion,
    publish := { }
  ).dependsOn(webCore % "test->test;compile->compile", search).settings(scalarifromSettings :_*)

  val cms = play.Project("cms", "1.0-SNAPSHOT", Seq.empty, path = file(cultureHubPath + "modules/cms")).settings(
    resolvers ++= commonResolvers,
    publish := {},
    routesImport += "extensions.Binders._"
  ).dependsOn(webCore % "test->test;compile->compile", dos).settings(scalarifromSettings :_*)

  val indexApi = play.Project("indexApi", "1.0-SNAPSHOT", Seq.empty, path = file(cultureHubPath + "modules/indexApi")).settings(
    resolvers ++= commonResolvers,
    publish := {}
  ).dependsOn(webCore % "test->test;compile->compile", dos, search).settings(scalarifromSettings :_*)

  val statistics = play.Project("statistics", "1.0-SNAPSHOT", Seq.empty, path = file(cultureHubPath + "modules/statistics")).settings(
    resolvers ++= commonResolvers,
    publish := { }
  ).dependsOn(webCore, dataSet, search).settings(scalarifromSettings :_*)

  val root = play.Project(appName, cultureHubVersion, appDependencies, settings = Defaults.defaultSettings ++ groovyTemplatesSettings, path = file(cultureHubPath)).settings(

    onLoadMessage := "May the force be with you",

    resolvers += Resolver.file("local-ivy-repo", file(Path.userHome + "/.ivy2/local"))(Resolver.ivyStylePatterns),
    resolvers ++= commonResolvers,

    sourceGenerators in Compile <+= groovyTemplatesList,

    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := false,
    publishTo := Some(delvingRepository(cultureHubVersion)),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),

    routesImport += "extensions.Binders._",

    testOptions in Test := Nil, // Required to use scalatest.

    parallelExecution in (ThisBuild) := false,

    scalaVersion in (ThisBuild) := buildScalaVersion,

    watchTransitiveSources <<= watchTransitiveSources map { (sources: Seq[java.io.File]) =>
      sources
        .filterNot(source => source.isFile && source.getPath.contains("app/views") && !source.getName.endsWith(".scala.html") && source.getName.endsWith(".html"))
        .filterNot(source => source.isDirectory && source.getPath.contains("app/views"))
    },

    // temporary workaround for https://github.com/playframework/Play20/issues/903
    // this breaks automatic reloading for changes in the routers but is still better than to reload at each request
    playMonitoredFiles <<= playMonitoredFiles map { (files: Seq[String]) =>
      files.filterNot(file => file.contains("src_managed"))
    }



  ).settings(scalarifromSettings :_*) // .settings(addArtifact(Artifact((appName + "-" + cultureHubVersion), "zip", "zip"), dist).settings :_*)
   .dependsOn(
    webCore                 % "test->test;compile->compile",
    thumbnail               % "test->test;compile->compile",
    deepZoom                % "test->test;compile->compile",
    hubNode                 % "test->test;compile->compile",
    search                  % "test->test;compile->compile",
    dataSet                 % "test->test;compile->compile",
    dos                     % "test->test;compile->compile",
    cms                     % "test->test;compile->compile",
    indexApi                % "test->test;compile->compile",
    statistics              % "test->test;compile->compile"
  ).aggregate(
    webCore,
    thumbnail,
    deepZoom,
    hubNode,
    search,
    dataSet,
    dos,
    cms,
    indexApi,
    statistics
  )


}
