package core.processing

import models.RecordDefinition
import eu.delving.MappingEngine
import play.api.Play
import play.api.Play.current
import core.mapping.MappingService
import collection.JavaConverters._

abstract class ProcessingSchema {

  val definition: RecordDefinition
  val namespaces: Map[String, String]
  val mapping: Option[String]
  val sourceSchema: String

  def isValidRecord(index: Int): Boolean

  lazy val prefix = definition.prefix
  lazy val hasMapping = mapping.isDefined
  lazy val javaNamespaces = namespaces.asJava
  lazy val engine: Option[MappingEngine] = mapping.map(new MappingEngine(_, Play.classloader, MappingService.recDefModel, javaNamespaces))
}
