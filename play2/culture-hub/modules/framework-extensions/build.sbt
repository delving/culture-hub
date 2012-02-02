name := "framework-extensions"

organization := "delving"

resolvers += "jahia" at "http://maven.jahia.org/maven2"

resolvers += "scala-tools" at "http://scala-tools.org/repo-releases/"

resolvers += "novus" at "http://repo.novus.com/snapshots/"

libraryDependencies ++= Seq(
        "play"                 %%    "play"                   % "2.0-RC1-SNAPSHOT",
        "com.mongodb.casbah"   %%    "casbah"                 % "2.1.5-1",
        "com.novus"            %%    "salat-core"             % "0.0.8-SNAPSHOT",
        "org.joda"             %     "joda-convert"           % "1.2",
        "org.codehaus.groovy"  %     "groovy"                 % "1.8.5",
        "commons-collections"  %     "commons-collections"    % "3.2.1",
        "commons-httpclient"   %     "commons-httpclient"     % "3.1",
        "commons-lang"         %     "commons-lang"           % "2.6",
        "com.jamonapi"         %     "jamon"                  % "2.7")
