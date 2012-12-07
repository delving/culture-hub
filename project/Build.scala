import sbt._
import scala._
import PlayProject._
import sbt.Keys._
import sbtbuildinfo.Plugin._
import eu.delving.templates.Plugin._

object Build extends sbt.Build {

  val cultureHub = SettingKey[String]("culture-hub", "Version of the CultureHub")
  val sipApp     = SettingKey[String]("sip-app", "Version of the SIP-App")
  val sipCore    = SettingKey[String]("sip-core", "Version of the SIP-Core")
  val schemaRepo = SettingKey[String]("schema-repo", "Version of the Schema Repository")

  val cultureHubPath = ""

  val appName = "culture-hub"
  val cultureHubVersion = "12.11"
  val sipAppVersion = "1.0.10-SNAPSHOT"
  val sipCoreVersion = "1.0.16-SNAPSHOT"
  val schemaRepoVersion = "1.0.11-SNAPSHOT"
  val playExtensionsVersion = "1.3.4-SNAPSHOT"

  val dosVersion = "1.5"

  val webCoreVersion = "1.0-SNAPSHOT"

  val delvingReleases = "Delving Releases Repository" at "http://development.delving.org:8081/nexus/content/repositories/releases"
  val delvingSnapshots = "Delving Snapshot Repository" at "http://development.delving.org:8081/nexus/content/repositories/snapshots"
  val delvingThirdParty = "Delving Third Party "  at "http://development.delving.org:8081/nexus/content/repositories/thirdparty"

  def delvingRepository(version: String) = if (version.endsWith("SNAPSHOT")) delvingSnapshots else delvingReleases

  val commonResolvers = Seq(
    "sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
    "sonatype releases" at "https://oss.sonatype.org/content/repositories/releases/",
    delvingSnapshots,
    delvingReleases,
    delvingThirdParty
  )

  val globalExclusions = <dependencies>
    <exclude org="commons-logging" module="commons-logging" />
  </dependencies>

  val appDependencies = Seq(
    "org.apache.amber"          %  "oauth2-authzserver"              % "0.2-SNAPSHOT",
    "org.apache.amber"          %  "oauth2-client"                   % "0.2-SNAPSHOT",
    "eu.delving"                %  "themes"                          % "1.0-SNAPSHOT"      changing()
  )

  val webCoreDependencies = Seq(
    "eu.delving"                %% "play2-extensions"                % playExtensionsVersion,

    "eu.delving"                %% "groovy-templates-plugin"         % "1.5.5-SNAPSHOT",
    "eu.delving"                %  "definitions"                     % "1.0",
    "eu.delving"                %  "sip-core"                        % sipCoreVersion,
    "eu.delving"                %  "schema-repo"                     % schemaRepoVersion,
    "eu.delving"                %% "basex-scala-client"              % "0.1-SNAPSHOT",

    "org.scala-tools.subcut"    %% "subcut"                          % "1.0",

    "org.apache.solr"           %  "solr-solrj"                      % "3.6.0",
    "org.apache.httpcomponents" %  "httpclient"                      % "4.1.2",
    "org.apache.httpcomponents" %  "httpmime"                        % "4.1.2",
    "org.apache.tika"           %  "tika-parsers"                    % "1.2",

    "org.scalesxml"             %% "scales-xml"                      % "0.3-RC6",

    "org.scalatest"             %% "scalatest"                       % "2.0.M4"              % "test",

    "org.slf4j"                 %  "jcl-over-slf4j"                  % "1.6.4"               % "compile"
  )

  val webCore = PlayProject("web-core", webCoreVersion, webCoreDependencies, file(cultureHubPath + "web-core/"), settings = Defaults.defaultSettings ++ buildInfoSettings).settings(
    organization := "eu.delving",
    version := webCoreVersion,
    publishTo := Some(delvingRepository(webCoreVersion)),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    publishMavenStyle := true,
    resolvers ++= commonResolvers,
    resolvers += "BaseX Repository" at "http://files.basex.org/maven",
    publish := { },
    testOptions in Test := Nil, // Required to use scalatest.
    cultureHub := cultureHubVersion,
    sipApp := sipAppVersion,
    sipCore := sipCoreVersion,
    schemaRepo := schemaRepoVersion,
    sourceGenerators in Compile <+= buildInfo,
    sourceGenerators in Test <+= buildInfo,
    buildInfoKeys := Seq[Scoped](name, cultureHub, scalaVersion, sbtVersion, sipApp, sipCore, schemaRepo),
    buildInfoPackage := "eu.delving.culturehub"
  )

  val dosDependencies = Seq(
    "eu.delving"                %% "play2-extensions"                 % playExtensionsVersion,
    "org.imgscalr"               %  "imgscalr-lib"                    % "4.2"
  )

  val dos = PlayProject("dos", dosVersion, dosDependencies, path = file(cultureHubPath + "modules/dos")).settings(
    resolvers ++= commonResolvers,
    publish := { }
  ).dependsOn(webCore % "test->test;compile->compile")

  val hubNode = PlayProject("hubNode", "1.0-SNAPSHOT", Seq.empty, path = file(cultureHubPath + "modules/hubNode")).settings(
    resolvers ++= commonResolvers,
    publish := {}
  ).dependsOn(webCore % "test->test;compile->compile")

  val dataSet = PlayProject("dataset", "1.0-SNAPSHOT", Seq.empty, path = file(cultureHubPath + "modules/dataset"), settings = Defaults.defaultSettings ++ buildInfoSettings).settings(
    resolvers ++= commonResolvers,
    sipApp := sipAppVersion,
    sipCore := sipCoreVersion,
    schemaRepo := schemaRepoVersion,
    sourceGenerators in Compile <+= buildInfo,
    buildInfoKeys := Seq[Scoped](name, version, scalaVersion, sbtVersion, sipApp, sipCore, schemaRepo),
    buildInfoPackage := "eu.delving.culturehub",
    publish := { }
  ).dependsOn(webCore % "test->test;compile->compile")

  val cms = PlayProject("cms", "1.0-SNAPSHOT", Seq.empty, path = file(cultureHubPath + "modules/cms")).settings(
    resolvers ++= commonResolvers,
    publish := {}
  ).dependsOn(webCore % "test->test;compile->compile", dos)

  val statistics = PlayProject("statistics", "1.0-SNAPSHOT", Seq.empty, path = file(cultureHubPath + "modules/statistics")).settings(
    resolvers ++= commonResolvers,
    publish := { }
  ).dependsOn(webCore, dataSet)

  val main = PlayProject(appName, cultureHubVersion, appDependencies, mainLang = SCALA, settings = Defaults.defaultSettings ++ buildInfoSettings ++ groovyTemplatesSettings).settings(

    onLoadMessage := "May the force be with you",

    resolvers += Resolver.file("local-ivy-repo", file(Path.userHome + "/.ivy2/local"))(Resolver.ivyStylePatterns),
    resolvers ++= commonResolvers,
    resolvers += "apache-snapshots" at "https://repository.apache.org/content/groups/snapshots-group/",

    sourceGenerators in Compile <+= groovyTemplatesList,

    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := false,
    publishTo := Some(delvingRepository(cultureHubVersion)),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),

    routesImport += "extensions.Binders._",

    parallelExecution in (ThisBuild) := false,

    ivyXML := globalExclusions

  ).settings(
    addArtifact(
      Artifact((appName + "-" + cultureHubVersion), "zip", "zip"), dist
    ).settings :_*
  ).dependsOn(
    webCore                 % "test->test;compile->compile",
    hubNode                 % "test->test;compile->compile",
    dataSet                 % "test->test;compile->compile",
    dos                     % "test->test;compile->compile",
    cms                     % "test->test;compile->compile",
    statistics              % "test->test;compile->compile"
  ).aggregate(
    webCore,
    hubNode,
    dataSet,
    dos,
    cms,
    statistics
  )


}
