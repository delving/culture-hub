/*
 * Copyright 2012 Delving B.V.
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

package models

import _root_.util.DomainConfigurationHandler
import core.schema.{Schema, SchemaProvider}
import xml.{Node, XML}
import play.api.{Logger, Play}
import play.api.Play.current
import java.net.URL
import core.SystemField
import eu.delving.schema.{SchemaType, SchemaRepository}
import collection.mutable

/**
 * Wrapper for a RecordDefinition
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class RecordDefinition(prefix: String,
                            schema: String,
                            schemaVersion: String,
                            namespace: String,               // the namespace of the format
                            allNamespaces: List[Namespace],  // all the namespaces occurring in this format (prefix, schema)
                            isFlat: Boolean                  // is this a flat record definition, i.e. can it be flat?
                            ) {

  def getNamespaces = allNamespaces.map(ns => (ns.prefix, ns.uri)).toMap[String, String]

}

case class Namespace(prefix: String, uri: String, schema: String)

case class FormatAccessControl(accessType: String = "none", accessKey: Option[String] = None) {
  def hasAccess(key: Option[String]) = isPublicAccess || (isProtectedAccess && key != None && accessKey == key)
  def isPublicAccess = accessType == "public"
  def isProtectedAccess = accessType == "protected"
  def isNoAccess = accessType == "none"
}

/**
 * Deals with loading RecordDefinitions, validation schemas and crosswalks
 *
 */
object RecordDefinition {

  val rawRecordDefinition = RecordDefinition(
    prefix = "raw",
    schema = "http://delving.eu/namespaces/raw",
    schemaVersion = "1.0.0",
    namespace = "http://delving.eu/namespaces/raw/schema.xsd",
    allNamespaces = List(Namespace("raw", "http://delving.eu/namespaces/raw", "http://delving.eu/namespaces/raw/schema.xsd")),
    isFlat = true
  )

  private val parsedRecordDefinitionsCache = new mutable.HashMap[String, RecordDefinition]()

  def getRecordDefinition(schema: Schema)(implicit configuration: DomainConfiguration): Option[RecordDefinition] = getRecordDefinition(schema.prefix, schema.version)

  def getRecordDefinition(prefix: String, version: String)(implicit configuration: DomainConfiguration): Option[RecordDefinition] = {
    if (Play.isProd) {
      parsedRecordDefinitionsCache.get(prefix + version).map { cached =>
        Some(cached)
      }.getOrElse {
        val definition = fetchRecordDefinition(prefix, version)
        if (definition.isDefined) {
          parsedRecordDefinitionsCache.put(prefix + version, definition.get)
        }
        definition
      }
    } else {
      fetchRecordDefinition(prefix, version)
    }

  }
  // TODO version crosswalk lookups
  def getCrosswalkResources(sourcePrefix: String)(implicit configuration: DomainConfiguration): Seq[URL] = {
    enabledCrosswalks(configuration).
      filter(_.startsWith(sourcePrefix)).
      flatMap(prefix => Play.resource("definitions/%s/%s-crosswalk.xml".format(sourcePrefix, prefix))).
      toSeq
  }

  private def fetchRecordDefinition(prefix: String, version: String)(implicit configuration: DomainConfiguration): Option[RecordDefinition] = {
    SchemaProvider.getSchema(prefix, version, SchemaType.RECORD_DEFINITION).flatMap { definition =>
      try {
        parseRecordDefinition(XML.loadString(definition), version)
      } catch {
        case t: Throwable =>
          Logger("CultureHub").error("Error while trying to parse recordDefintion %s %s".format(prefix, version), t)
          None
      }
    }
  }

  private def parseRecordDefinition(node: Node, version: String): Option[RecordDefinition] = {
    val prefix = (node \ "@prefix" ).text
    val isFlat = node.attribute("flat").isDefined && (node \ "@flat" text).length > 0 && (node \ "@flat" text).toBoolean
    val recordDefinitionNamespace: Node = node \ "namespaces" \ "namespace" find { _.attributes("prefix").exists(_.text == prefix) } getOrElse (return None)

    val allNamespaces = (node \ "namespaces" \ "namespace").map(
      n => Namespace(
        n.attribute("prefix").get.text,
        n.attribute("uri").get.text,
        n.attribute("schema").get.text
      )).toList

    Some(
      RecordDefinition(
        recordDefinitionNamespace \ "@prefix" text,
        recordDefinitionNamespace \ "@schema" text,
        version,
        recordDefinitionNamespace \ "@uri" text,
        allNamespaces,
        isFlat
      )
    )
  }

  private lazy val enabledCrosswalks: Map[DomainConfiguration, Seq[String]] = DomainConfigurationHandler.domainConfigurations.
      map(configuration => (configuration -> configuration.crossWalks)).toMap

}
