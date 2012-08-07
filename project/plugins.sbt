logLevel := Level.Warn

resolvers ++= Seq(
    DefaultMavenRepository,
    Resolver.url("Play", url("http://download.playframework.org/ivy-releases/"))(Resolver.ivyStylePatterns),
    Resolver.file("local-ivy-repo", file(Path.userHome + "/.ivy2/local"))(Resolver.ivyStylePatterns),
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    "Delving Releases Repository" at "http://development.delving.org:8081/nexus/content/groups/public",
    "Delving Snapshot Repository" at "http://development.delving.org:8081/nexus/content/repositories/snapshots",
    Resolver.url("sbt-buildinfo-resolver-0", url("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)
)

addSbtPlugin("com.eed3si9n" %% "sbt-buildinfo" % "0.1.2")

addSbtPlugin("eu.delving" %% "groovy-templates-sbt-plugin" % "1.4")

addSbtPlugin("play" % "sbt-plugin" % "2.0.3")
