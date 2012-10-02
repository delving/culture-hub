import sbt._
import scala._
import PlayProject._
import sbt.Keys._
import sbtbuildinfo.Plugin._
import eu.delving.templates.Plugin._

object Build extends sbt.Build {

  val cultureHub     = SettingKey[String]("cultureHub", "Version of the CultureHub")
  val sipApp     = SettingKey[String]("sip-app", "Version of the SIP-App")
  val sipCore    = SettingKey[String]("sip-core", "Version of the SIP-Core")
  val schemaRepo = SettingKey[String]("schema-repo", "Version of the Schema Repository")

  val appName = "culture-hub"
  val cultureHubVersion = "12.09-SNAPSHOT"
  val sipAppVersion = "1.0.10-SNAPSHOT"
  val sipCoreVersion = "1.0.12-SNAPSHOT"
  val schemaRepoVersion = "1.0.11-SNAPSHOT"
  val playExtensionsVersion = "1.3.3"

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
    "org.seleniumhq.selenium"   %  "selenium-firefox-driver"         % "2.25.0"            % "test",
    "org.seleniumhq.selenium"   %  "selenium-htmlunit-driver"        % "2.25.0"            % "test",
    "org.fluentlenium"          %  "fluentlenium-core"               % "0.7.2"             % "test",
    "net.sourceforge.htmlunit"  %  "htmlunit"                        % "2.10"              % "test",
    "eu.delving"                %% "themes"                          % "1.0-SNAPSHOT"      changing()
  )

  val webCoreDependencies = Seq(
    "eu.delving"                %% "play2-extensions"                % playExtensionsVersion,

    "eu.delving"                %  "definitions"                     % "1.0",
    "eu.delving"                %  "sip-core"                        % sipCoreVersion,
    "eu.delving"                %  "schema-repo"                     % schemaRepoVersion,
    "eu.delving"                %% "basex-scala-client"              % "0.1-SNAPSHOT",

    "org.scala-tools.subcut"    %% "subcut"                          % "1.0",

    "org.apache.solr"           %  "solr-solrj"                      % "3.6.0",
    "org.apache.httpcomponents" %  "httpclient"                      % "4.1.2",
    "org.apache.httpcomponents" %  "httpmime"                        % "4.1.2",
    "org.apache.tika"           %  "tika-parsers"                    % "1.0",

    "org.scalesxml"             %% "scales-xml"                      % "0.3-RC6",

    "org.scalatest"             %% "scalatest"                       % "2.0.M3"              % "test"
  )

  val webCore = PlayProject("web-core", webCoreVersion, webCoreDependencies, file("web-core/")).settings(
    organization := "eu.delving",
    version := webCoreVersion,
    publishTo := Some(delvingRepository(webCoreVersion)),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    publishMavenStyle := true,
    resolvers ++= commonResolvers,
    resolvers += "BaseX Repository" at "http://files.basex.org/maven",
    publish := { },
    testOptions in Test := Nil // Required to use scalatest.
  )

  val dosDependencies = Seq(
    "eu.delving"                %% "play2-extensions"                 % playExtensionsVersion,
    "org.imgscalr"               %  "imgscalr-lib"                    % "4.2"
  )

  val dos = PlayProject("dos", dosVersion, dosDependencies, path = file("modules/dos")).settings(
    resolvers ++= commonResolvers,
    publish := { }
  ).dependsOn(webCore % "test->test;compile->compile")

  val dataSet = PlayProject("dataset", "1.0-SNAPSHOT", Seq.empty, path = file("modules/dataset"), settings = Defaults.defaultSettings ++ buildInfoSettings).settings(
    resolvers ++= commonResolvers,
    sipApp := sipAppVersion,
    sipCore := sipCoreVersion,
    schemaRepo := schemaRepoVersion,
    sourceGenerators in Compile <+= buildInfo,
    buildInfoKeys := Seq[Scoped](name, version, scalaVersion, sbtVersion, sipApp, sipCore, schemaRepo),
    buildInfoPackage := "eu.delving.culturehub",
    publish := { }
  ).dependsOn(webCore % "test->test;compile->compile")

  val statistics = PlayProject("statistics", "1.0-SNAPSHOT", Seq.empty, path = file("modules/statistics")).settings(
    resolvers ++= commonResolvers,
    publish := { }
  ).dependsOn(webCore, dataSet)

  val main = PlayProject(appName, cultureHubVersion, appDependencies, mainLang = SCALA, settings = Defaults.defaultSettings ++ buildInfoSettings ++ groovyTemplatesSettings).settings(

    onLoadMessage := "May the force be with you",

    resolvers += Resolver.file("local-ivy-repo", file(Path.userHome + "/.ivy2/local"))(Resolver.ivyStylePatterns),
    resolvers ++= commonResolvers,
    resolvers += "apache-snapshots" at "https://repository.apache.org/content/groups/snapshots-group/",

    sourceGenerators in Compile <+= groovyTemplatesList,

    cultureHub := cultureHubVersion,
    sipApp := sipAppVersion,
    sipCore := sipCoreVersion,
    schemaRepo := schemaRepoVersion,
    sourceGenerators in Compile <+= buildInfo,
    buildInfoKeys := Seq[Scoped](name, cultureHub, scalaVersion, sbtVersion, sipApp, sipCore, schemaRepo),
    buildInfoPackage := "eu.delving.culturehub",

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
    statistics              % "test->test;compile->compile"
  ).aggregate(
    webCore,
    dataSet,
    dos,
    statistics
  )


}
