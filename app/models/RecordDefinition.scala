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

import xml.{Node, XML}
import play.api.Play
import play.api.Play.current
import java.net.URL

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class RecordDefinition(prefix: String,
                            schema: String,
                            namespace: String,               // the namespace of the format
                            allNamespaces: List[Namespace],  // all the namespaces occurring in this format (prefix, schema)
                            roles: List[Role] = List.empty,  // roles that are described in the RecordDefinition
                            summaryFields: List[SummaryField] = List.empty, // summary fields
                            searchFields: List[SearchField] = List.empty, // search fields
                            isFlat: Boolean                  // is this a flat record definition, i.e. can it be flat?
                            ) {

  def getNamespaces = allNamespaces.map(ns => (ns.prefix, ns.uri)).toMap[String, String]
}

case class Namespace(prefix: String, uri: String, schema: String)

case class Role(key: String, description: String, prefix: String)

case class FormatAccessControl(accessType: String = "none", accessKey: Option[String] = None) {
  def hasAccess(key: Option[String]) = isPublicAccess || (isProtectedAccess && key != None && accessKey == key)
  def isPublicAccess = accessType == "public"
  def isProtectedAccess = accessType == "protected"
  def isNoAccess = accessType == "none"
}

case class SummaryField(name: String, xpath: String) {

  def isValid = try {
    eu.delving.metadata.SummaryField.valueOf(name)
    true
  } catch {
    case _ => false
  }

  def tag = "delving_" + eu.delving.metadata.SummaryField.valueOf(name).tag
}

case class SearchField(name: String, xpath: String, fieldType: String)

object RecordDefinition {

  val RECORD_DEFINITION_SUFFIX = "-record-definition.xml"
  val VALIDATION_SCHEMA_SUFFIX = "-validation.xsd"
  val CROSSWALK_SUFFIX = "-crosswalk.xml"

  val enabledDefinitions = Play.configuration.getString("cultureHub.recordDefinitions").getOrElse("").split(",").map(_.trim())

  val enabledCrosswalks = Play.configuration.getString("cultureHub.crossWalks").getOrElse("").split(",").map(_.trim())

  def recordDefinitions = parseRecordDefinitions

  def getRecordDefinition(prefix: String): Option[RecordDefinition] = recordDefinitions.find(_.prefix == prefix)

  def getRecordDefinitionResources = {
    enabledDefinitions.flatMap(prefix => Play.resource("definitions/%s/%s-record-definition.xml".format(prefix, prefix)))
  }

  def getCrosswalkResources(sourcePrefix: String): Seq[URL] = {
    enabledCrosswalks.filter(_.startsWith(sourcePrefix)).flatMap(prefix => Play.resource("definitions/%s/%s-crosswalk.xml".format(sourcePrefix, prefix))).toSeq
  }

  private def parseRecordDefinitions: List[RecordDefinition] = {
    val definitionContent = getRecordDefinitionResources.map { r => XML.load(r) }
    definitionContent.flatMap(parseRecordDefinition(_)).toList
  }

  private def parseRecordDefinition(node: Node): Option[RecordDefinition] = {
    val prefix = (node \ "@prefix" ).text
    val isFlat = node.attribute("flat").isDefined && (node \ "@flat" text).length > 0 && (node \ "@flat" text).toBoolean
    val recordDefinitionNamespace: Node = node \ "namespaces" \ "namespace" find { _.attributes("prefix").exists(_.text == prefix) } getOrElse (return None)

    val allNamespaces = (node \ "namespaces" \ "namespace").map(
      n => Namespace(
        n.attribute("prefix").get.text,
        n.attribute("uri").get.text,
        n.attribute("schema").get.text
      )).toList

    val roles = (node \ "roles" \ "role").map(r => Role((r \ "@key").text, (r \ "@description").text, prefix)).toList
    val summaryFields = (node \ "summaryFields" \ "summaryField").map(sf => SummaryField((sf \ "@name").text, (sf \ "@xpath").text)).toList
    val searchFields = (node \ "searchFields" \ "searchField").map(sf => SearchField((sf \ "@name").text, (sf \ "@xpath").text, (if(sf.attribute("fieldType").isDefined) (sf \ "@fieldType").text else "text"))).toList

    Some(
      RecordDefinition(
        recordDefinitionNamespace \ "@prefix" text,
        recordDefinitionNamespace \ "@schema" text,
        recordDefinitionNamespace \ "@uri" text,
        allNamespaces,
        roles,
        summaryFields,
        searchFields,
        isFlat
      )
    )
  }

}
