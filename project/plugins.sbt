logLevel := Level.Warn

resolvers ++= Seq(
    DefaultMavenRepository,
    Resolver.url("Play", url("http://download.playframework.org/ivy-releases/"))(Resolver.ivyStylePatterns),
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    "sbt-idea-repo" at "http://mpeltonen.github.com/maven/",
    "jahia" at "http://maven.jahia.org/maven2",
    Resolver.url("sbt-buildinfo-resolver-0", url("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)
)

addSbtPlugin("com.github.mpeltonen" %% "sbt-idea" % "1.0.0")

addSbtPlugin("com.eed3si9n" %% "sbt-buildinfo" % "0.1.1")

addSbtPlugin("eu.delving" %% "groovy-templates-plugin" % "1.2-SNAPSHOT")

addSbtPlugin("play" % "sbt-plugin" % "2.0")
