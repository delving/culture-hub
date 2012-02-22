import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

  val appName = "culture-hub"
  val appVersion = "1.0"

  val dosVersion = "1.5"

  val delvingReleases = "Delving Releases Repository" at "http://development.delving.org:8081/nexus/content/groups/public"
  val delvingSnapshots = "Delving Snapshot Repository" at "http://development.delving.org:8081/nexus/content/repositories/snapshots"

  val appDependencies = Seq(
    "org.apache.amber"          %  "oauth2-authzserver"              % "0.2-SNAPSHOT",
    "org.apache.amber"          %  "oauth2-client"                   % "0.2-SNAPSHOT",
    "net.liftweb"               %% "lift-json-ext"                   % "2.4-M4",

    "eu.delving"                %  "sip-core"                        % "0.4.6-SNAPSHOT",
    "eu.delving"                %  "sip-creator"                     % "0.4.6-SNAPSHOT",

    "org.apache.solr"           %  "solr-solrj"                      % "3.4.0",
    "org.apache.tika"           %  "tika-parsers"                    % "1.0"

  )

  val dosDependencies = Seq(
    "com.thebuzzmedia"          %  "imgscalr-lib"                     % "3.2"
  )

  val frameworkExtensions = Project("framework-extensions", file("modules/framework-extensions")).settings(
    resolvers += delvingSnapshots,
    resolvers += delvingReleases
  )

  val dos = PlayProject("dos", dosVersion, dosDependencies, path = file("modules/dos")).dependsOn(frameworkExtensions).settings(
    resolvers += "buzzmedia" at "http://maven.thebuzzmedia.com",
    resolvers += delvingSnapshots,
    resolvers += delvingReleases
  )

  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(

    resolvers += Resolver.file("local-ivy-repo", file(Path.userHome + "/.ivy2/local"))(Resolver.ivyStylePatterns),
    resolvers += "jahia" at "http://maven.jahia.org/maven2",
    resolvers += "apache-snapshots" at "https://repository.apache.org/content/groups/snapshots-group/",
    resolvers += delvingSnapshots,
    resolvers += delvingReleases,

    routesImport += "extensions.Binders._"

  ).dependsOn(frameworkExtensions, dos)

}