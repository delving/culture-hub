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

import java.io.File
import xml.{Node, XML}
import play.api.Play
import play.api.Play.current

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class RecordDefinition(prefix: String,
                            schema: String,
                            namespace: String,
                            accessKeyRequired: Boolean = false,
                            roles: List[Role] = List.empty)

case class Role(key: String, description: String, prefix: String)

object RecordDefinition {

  val RECORD_DEFINITION_SUFFIX = "-record-definition.xml"

  val enabledDefinitions = Play.configuration.getString("culturehub.recordDefinitions").getOrElse("").split(",").map(_.trim())

  def recordDefinitions = parseRecordDefinitions

  def getRecordDefinitionFiles: Seq[File] = {
    val conf = new File("conf/")
    conf.listFiles()
      .filter(f => f.isFile && f.getName.endsWith(RECORD_DEFINITION_SUFFIX))
      .filter(f => enabledDefinitions.contains(f.getName.substring(0, f.getName.indexOf(RECORD_DEFINITION_SUFFIX))))
  }

  private def parseRecordDefinitions: List[RecordDefinition] = {
    val definitionContent = getRecordDefinitionFiles.map { f => XML.loadFile(f) }
    definitionContent.flatMap(parseRecordDefinition(_)).toList
  }

  private def parseRecordDefinition(node: Node): Option[RecordDefinition] = {
    val prefix = (node \ "@prefix" ).text
    val recordDefinitionNamespace: Node = node \ "namespaces" \ "namespace" find { _.attributes("prefix").exists(_.text == prefix) } getOrElse (return None)
    val roles = (node \ "roles" \ "role").map(r => Role((r \ "@key").text, (r \ "@description").text, prefix)).toList
    Some(
      RecordDefinition(
        recordDefinitionNamespace \ "@prefix" text,
        recordDefinitionNamespace \ "@schema" text,
        recordDefinitionNamespace \ "@uri" text,
        false,
        roles
      )
    )
  }

}
