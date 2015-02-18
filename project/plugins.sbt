logLevel := Level.Info

resolvers ++= Seq(
    Resolver.file("local-ivy-repo", file(Path.userHome + "/.ivy2/local"))(Resolver.ivyStylePatterns),
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    "Delving Repository" at "http://artifactory.delving.org/artifactory/delving",
    Resolver.url("sbt-buildinfo-resolver-0", url("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)
)

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.2.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.0.1")

addSbtPlugin("eu.delving" % "groovy-templates-sbt-plugin" % "1.6.2")

addSbtPlugin("play" % "sbt-plugin" % "2.1.0")
