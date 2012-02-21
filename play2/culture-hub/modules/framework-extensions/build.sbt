name := "framework-extensions"

organization := "delving"

resolvers += "scala-tools" at "http://scala-tools.org/repo-releases/"

resolvers += "novus" at "http://repo.novus.com/snapshots/"

resolvers += "delving-snapshots" at "http://development.delving.org:8081/nexus/content/repositories/snapshots/"

resolvers += "delving-releases" at "http://development.delving.org:8081/nexus/content/repositories/releases"

libraryDependencies ++= Seq(
        "play"                 %%    "play"                        % "2.0-RC3-SNAPSHOT",
        "eu.delving"           %%    "groovy-templates-plugin"     % "0.1-SNAPSHOT",
        "com.mongodb.casbah"   %%    "casbah"                      % "2.1.5-1",
        "com.novus"            %%    "salat-core"                  % "0.0.8-SNAPSHOT",
        "org.joda"             %     "joda-convert"                % "1.2",
        "commons-collections"  %     "commons-collections"         % "3.2.1",
        "commons-httpclient"   %     "commons-httpclient"          % "3.1",
        "commons-io"           %     "commons-io"                  % "2.1",
        "org.apache.commons"   %     "commons-email"               % "1.2",
        "commons-lang"         %     "commons-lang"                % "2.6")