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

package util

import play.api.Play
import play.api.Play.current
import collection.immutable.HashMap
import models.DomainConfiguration
import extensions.ConfigurationException

/**
 * Takes care of loading domain-specific configuration
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @since 3/9/11 3:25 PM
 */
object DomainConfigurationHandler {

  private var domainConfigurationList: Seq[DomainConfiguration] = Seq.empty
  private var domainConfigurations: Seq[(String, DomainConfiguration)] = Seq.empty
  private var domainLookupCache: HashMap[String, DomainConfiguration] = HashMap.empty

  def getConfigurationNames: java.util.Set[String] = {
    val set: java.util.Set[String] = new java.util.TreeSet[String]
    domainConfigurationList.foreach(configuration => set.add(configuration.name))
    set
  }

  def startup() {
    domainConfigurationList = DomainConfiguration.getAll
    domainConfigurations = toDomainList(domainConfigurationList)
    domainLookupCache = HashMap.empty

    if (!getDefaultConfiguration.isDefined) {
      throw ConfigurationException("No default domain configuration could be found!")
    }
  }

  def hasSingleConfiguration: Boolean = domainConfigurationList.length == 1

  def hasConfiguration(configurationName: String): Boolean = !domainConfigurationList.filter(configuration => configuration.name == configurationName).isEmpty

  def getDefaultConfiguration = domainConfigurationList.filter(_.name == current.configuration.getString("themes.defaultTheme").getOrElse("default")).headOption

  def getByName(name: String) = {
    val configuration = domainConfigurationList.filter(_.name.equalsIgnoreCase(name))
    if (!configuration.isEmpty) configuration.head
    else getDefaultConfiguration.get
  }

  def getByDomain(domain: String): DomainConfiguration = {
    if (Play.isDev) {
      startup()
    }
    if (hasSingleConfiguration) {
      getDefaultConfiguration.get
    } else {
      // FIXME - this is, of course, vulnerable. Implement correct algorithmic solution not relying on fold.
      if(!domainLookupCache.contains(domain)) {
        // fetch by longest matching domain
        val configuration = domainConfigurations.foldLeft(("#", getDefaultConfiguration.get)) {
          (r: (String, DomainConfiguration), c: (String, DomainConfiguration)) => {
            val rMatches = domain.startsWith(r._1)
            val cMatches = domain.startsWith(c._1)
            val rLonger = r._1.length() > c._1.length()

            if (rMatches && cMatches && rLonger) r
            else if (rMatches && cMatches && !rLonger) c
            else if (rMatches && !cMatches) r
            else if (cMatches && !rMatches) c
            else r // default
          }
        }._2
        domainLookupCache = domainLookupCache + (domain -> configuration)
      }
      domainLookupCache(domain)
    }
  }

  private def toDomainList(domainList: Seq[DomainConfiguration]) = domainList.flatMap(t => t.domains.map((_, t))).sortBy(_._1.length)

}


