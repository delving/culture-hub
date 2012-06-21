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
import models.PortalTheme
import extensions.ConfigurationException

/**
 * ThemHandler taking care of loading themes
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @since 3/9/11 3:25 PM
 */
object ThemeHandler {

  private var themeList: Seq[PortalTheme] = Seq.empty
  private var domainList: Seq[(String, PortalTheme)] = Seq.empty
  private var domainLookupCache: HashMap[String, PortalTheme] = HashMap.empty

  def getThemeNames: java.util.Set[String] = {
    val set: java.util.Set[String] = new java.util.TreeSet[String]
    themeList.foreach(theme => set.add(theme.name))
    set
  }

  def startup() {
    themeList = PortalTheme.getAll
    domainList = toDomainList(themeList)
    domainLookupCache = HashMap.empty

    if (!getDefaultTheme.isDefined) {
      throw ConfigurationException("No default theme could be found!")
    }
  }

  def hasSingleTheme: Boolean = themeList.length == 1

  def hasTheme(themeName: String): Boolean = !themeList.filter(theme => theme.name == themeName).isEmpty

  def getDefaultTheme = themeList.filter(_.name == current.configuration.getString("themes.defaultTheme").getOrElse("default")).headOption

  def getByThemeName(name: String) = {
    val theme = themeList.filter(_.name.equalsIgnoreCase(name))
    if (!theme.isEmpty) theme.head
    else getDefaultTheme.get
  }

  def getByDomain(domain: String): PortalTheme = {
    if (Play.isDev) {
      startup()
    }
    if (hasSingleTheme) {
      getDefaultTheme.get
    } else {
      // FIXME - this is, of course, vulnerable. Implement correct algorithmic solution not relying on fold.
      if(!domainLookupCache.contains(domain)) {
        // fetch by longest matching domain
        val theme = domainList.foldLeft(("#", getDefaultTheme.get)) {
          (r: (String, PortalTheme), c: (String, PortalTheme)) => {
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
        domainLookupCache = domainLookupCache + (domain -> theme)
      }
      domainLookupCache(domain)
    }
  }

  private def toDomainList(themeList: Seq[PortalTheme]) = themeList.flatMap(t => t.domains.map((_, t))).sortBy(_._1.length)

}


