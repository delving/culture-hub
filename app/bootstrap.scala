/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.lang.Throwable
import java.util.Properties
import java.util.regex.{Pattern, Matcher}
import javax.imageio.spi.IIORegistry
import models.salatContext._
import models.User
import play.exceptions.ConfigurationException
import play.jobs._
import play.libs.IO
import play.test._
import play.{Logger, Play}
import util.{MongoCacheImpl, ScalaCacheAccessor, ThemeHandler}

@OnApplicationStart class BootStrap extends Job {

  override def doJob {

    val start = System.currentTimeMillis()

    val greeting = """
 ____       _       _
|  _ \  ___| |_   _(_)_ __   __ _
| | | |/ _ \ \ \ / / | '_ \ / _` |
| |_| |  __/ |\ V /| | | | | (_| |
|____/ \___|_| \_/ |_|_| |_|\__, |
                            |___/
  ____      _ _                  _   _       _
 / ___|   _| | |_ _   _ _ __ ___| | | |_   _| |__
| |  | | | | | __| | | | '__/ _ \ |_| | | | | '_ \
| |__| |_| | | |_| |_| | | |  __/  _  | |_| | |_) |
 \____\__,_|_|\__|\__,_|_|  \___|_| |_|\__,_|_.__/
 """

    println(greeting)

    import scala.collection.JavaConversions._

    // also retrieve user-based properties when running in test mode
    if (Play.id == "test") {
      val user = Option(System.getProperty("user")).getOrElse(throw new ConfigurationException("When running in test mode, specify the user using the JVM arg '-Duser=melvin'"))

      val pattern: Pattern = Pattern.compile("^%([a-zA-Z0-9_\\-]+)\\.(.*)$")
      val config: Properties = IO.readUtf8Properties(Play.conf.inputstream())
      for (key <- config.keySet()) {
        val matcher: Matcher = pattern.matcher(key + "")
        if (matcher.matches) {
          val instance = matcher.group(1)
          if (instance == user) {
            Play.configuration.put(matcher.group(2), config.get(key).toString.trim)
          }
        }
      }
    }

    // Check if all required launch properties are there
    Yaml[List[String]]("launchProperties.yml") map {
      key =>
        val value: String = Play.configuration.getProperty(key)
        if (value == null || value.trim().length() == 0) {
          throw new ConfigurationException("Required configuration property '" + key + "' is not configured. If you are running in dev mode, make sure you started the framework with 'play run --%youruser'")
        }
    }

    println("""Starting up with node name "%s"""".format(getNode))

    // mongo-based cache, to get press to work without having to deploy memcached
    ScalaCacheAccessor.set(new MongoCacheImpl)

    // TODO bootstrap all lazy Mongo connections here

    // see http://download.oracle.com/javase/1.4.2/docs/api/javax/imageio/spi/IIORegistry.html#registerApplicationClasspathSpis():
    // "Service providers found on the system classpath (e.g., the jre/lib/ext directory in Sun's implementation of J2SDK) are automatically loaded as soon as this class is instantiated."
    // this causes a ConcurrentModificationException with Play's classloading mechanism

    IIORegistry.getDefaultInstance

    ThemeHandler.startup()

    // Import initial data if the database is empty
    if (Play.mode == Play.Mode.DEV && User.count() == 0) {
      println("No test data found, reloading it")
      new util.TestDataLoader
    }

    println("Started up in %s ms".format(System.currentTimeMillis() - start))
  }

  override def onException(e: Throwable) {
    Logger.fatal(e, "Error while starting the CultureHub, shuting down")
    System.exit(-1)
  }
}