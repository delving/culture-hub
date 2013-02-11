package core.processing

import models.RecordDefinition
import eu.delving.{ MappingEngineFactory, MappingEngine }
import play.api.Play
import play.api.Play.current
import core.mapping.MappingService
import collection.JavaConverters._
import java.util.concurrent.{ Executors, ExecutorService }

abstract class ProcessingSchema {

  val definition: RecordDefinition
  val namespaces: Map[String, String]
  val mapping: Option[String]
  val sourceSchema: String

  def isValidRecord(index: Int): Boolean

  lazy val prefix = definition.prefix
  lazy val hasMapping = mapping.isDefined
  lazy val schemaVersion = definition.version
  lazy val javaNamespaces = namespaces.asJava

  override def toString: String = prefix
}