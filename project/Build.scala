import sbt._
import scala._
import sbt.Keys._
import com.typesafe.sbt._
import play.Project._
import sbtbuildinfo.Plugin._
import eu.delving.templates.Plugin._

object Build extends sbt.Build {

  val cultureHub = SettingKey[String]("culture-hub", "Version of the CultureHub")
  val sipApp     = SettingKey[String]("sip-app", "Version of the SIP-App")
  val sipCore    = SettingKey[String]("sip-core", "Version of the SIP-Core")
  val schemaRepo = SettingKey[String]("schema-repo", "Version of the Schema Repository")

  val cultureHubVersion = "13.04-SNAPSHOT"
  val sipAppVersion = "1.1.3"
  val sipCoreVersion = "1.1.3"
  val schemaRepoVersion = "1.1.3"
  val playExtensionsVersion = "1.4-SNAPSHOT"

  val buildScalaVersion = "2.10.0"

  val delvingReleases = "Delving Releases Repository" at "http://nexus.delving.org/nexus/content/repositories/releases"
  val delvingSnapshots = "Delving Snapshot Repository" at "http://nexus.delving.org/nexus/content/repositories/snapshots"

  val commonResolvers = Seq(
    "Delving Proxy repository" at "http://nexus.delving.org/nexus/content/groups/public/"
  )

  def delvingRepository(version: String) = if (version.endsWith("SNAPSHOT")) delvingSnapshots else delvingReleases

  val scalarifromSettings = SbtScalariform.scalariformSettings

  val appDependencies = Seq(
    "org.apache.amber"          %  "amber-oauth2-authzserver"        % "0.22-incubating",
    "org.apache.amber"          %  "amber-oauth2-client"             % "0.22-incubating",
    "eu.delving"                %  "themes"                          % "1.0-SNAPSHOT"      changing()
  )


    // ~~~ core

  val webCoreDependencies = Seq(
    "eu.delving"                %% "play2-extensions"                % playExtensionsVersion,

    "eu.delving"                %  "definitions"                     % "1.0",
    "eu.delving"                %  "sip-core"                        % sipCoreVersion,
    "eu.delving"                %  "schema-repo"                     % schemaRepoVersion,
    "eu.delving"                %% "basex-scala-client"              % "0.6.1",

    "com.escalatesoft.subcut"   %% "subcut"                          % "2.0" exclude ("org.scalatest", "scalatest"),
    "com.yammer.metrics"        %  "metrics-core"                    % "2.2.0",
    "nl.grons"                  %% "metrics-scala"                   % "2.2.0",

    "org.apache.httpcomponents" %  "httpclient"                      % "4.1.2",
    "org.apache.httpcomponents" %  "httpmime"                        % "4.1.2",

    "org.scalesxml"             %% "scales-xml"                      % "0.4.4",

    "org.scalatest"             %% "scalatest"                       % "2.0.M5b"             % "test",

    // temporary until https://play.lighthouseapp.com/projects/82401-play-20/tickets/970-xpathselecttext-regression is fixed
    "org.apache.ws.commons"             %    "ws-commons-util"          %   "1.0.1" exclude("junit", "junit")
  )

  val webCore = play.Project("web-core", "1.0-SNAPSHOT", webCoreDependencies, file("web-core/"), settings = Defaults.defaultSettings ++ buildInfoSettings).settings(
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

  lazy val search = play.Project("search", "1.0-SNAPSHOT", Seq.empty, path = file("modules/search")).settings(
    resolvers ++= commonResolvers,
    publish := { }
  ).dependsOn(webCore % "test->test;compile->compile").settings(scalarifromSettings :_*)

  lazy val dataset = play.Project("dataset", "1.0-SNAPSHOT", Seq.empty, path = file("modules/dataset")).settings(
    resolvers ++= commonResolvers,
    publish := { }
  ).dependsOn(webCore % "test->test;compile->compile", search % "test->test;compile->compile").settings(scalarifromSettings :_*)

  // ~~~ dynamic modules, to avoid hard-coded definitions

  val excludes = Seq("cms", "search", "dataset", "thumbnail", "deepZoom", "dos", "simple-document-upload")

  def discoverModules(base: File, dir: String): Seq[Project] = {
    val dirs: Seq[sbt.File] = if((base / dir).listFiles != null) (base / dir).listFiles else Seq.empty[sbt.File]
    for (x <- dirs if x.isDirectory && !excludes.contains(x.getName)) yield
        play.Project(x.getName, "1.0-SNAPSHOT", Seq.empty, path = x).settings(
          resolvers ++= commonResolvers,
          publish := { }
        ).dependsOn(webCore % "test->test;compile->compile", search % "test->test;compile->compile", dataset % "test->test;compile->compile").settings(scalarifromSettings :_*)
  }

  def modules(base: File): Seq[Project] = discoverModules(base, "modules") ++ discoverModules(base, "additionalModules")

  def module(base: File, id: String): Project = modules(base).find(_.id == id).get


  // the following projects have dependencies on other modules, and need to be declared separately

  lazy val dos = play.Project("dos", "1.0-SNAPSHOT", Seq.empty, path = file("modules/dos")).settings(
    resolvers ++= commonResolvers,
    publish := { },
    libraryDependencies += "eu.delving"                %% "play2-extensions"                % playExtensionsVersion,
    routesImport += "extensions.Binders._"
  ).dependsOn(webCore % "test->test;compile->compile").settings(scalarifromSettings :_*)

  def cms(base: File) = play.Project("cms", "1.0-SNAPSHOT", Seq.empty, path = file("modules/cms")).settings(
  resolvers ++= commonResolvers,
  publish := { },
  libraryDependencies += "eu.delving"                %% "play2-extensions"                % playExtensionsVersion,
  routesImport += "extensions.Binders._"
  ).dependsOn(webCore % "test->test;compile->compile", dos % "test->test;compile->compile").settings(scalarifromSettings :_*)

  lazy val simpleDocumentUpload = play.Project("simple-document-upload", "1.0-SNAPSHOT", Seq.empty, path = file("additionalModules/simple-document-upload")).settings(
    resolvers ++= commonResolvers,
    testOptions in Test := Nil, // Required to use scalatest.
    publish := { }
  ).dependsOn(webCore % "test->test;compile->compile", dos).settings(scalarifromSettings :_*)


  def allModules(base: File) = Seq(webCore, search, dataset, dos, simpleDocumentUpload, cms(base)) ++ modules(base)

  def allModuleReferences(base: File) = allModules(base).map {x => x: ProjectReference }

  override def projectDefinitions(baseDirectory: File): Seq[Project] = allModules(baseDirectory) ++ Seq(root(baseDirectory))



  def root(base: File): Project = play.Project("culture-hub", cultureHubVersion, appDependencies, settings = Defaults.defaultSettings ++ groovyTemplatesSettings).settings(

    onLoadMessage := "May the force be with you",

    sourceGenerators in Compile <+= groovyTemplatesList,

    resolvers += Resolver.file("local-ivy-repo", file(Path.userHome + "/.ivy2/local"))(Resolver.ivyStylePatterns),
    resolvers ++= commonResolvers,
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := false,
    publishTo := Some(delvingRepository(cultureHubVersion)),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),


    routesImport += "extensions.Binders._",

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

  ).settings(scalarifromSettings :_*)
   .dependsOn(allModules(base) map { x => x % "test->test;compile->compile"} : _*)
   .aggregate(allModuleReferences(base) : _*)


}
