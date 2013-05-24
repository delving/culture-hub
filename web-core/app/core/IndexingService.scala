package core

import models.OrganizationConfiguration
import org.joda.time.DateTime
import scala.collection.mutable

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait IndexingService {

  type IndexDocument = Map[String, mutable.Set[Any]]

  /**
   * Stages a document for indexing, and applies all generic delving mechanisms on top
   */
  def stageForIndexing(doc: IndexDocument)(implicit configuration: OrganizationConfiguration)

  def commit(implicit configuration: OrganizationConfiguration)

  def deleteById(id: String)(implicit configuration: OrganizationConfiguration)

  def deleteByQuery(query: String)(implicit configuration: OrganizationConfiguration)

  def deleteBySpec(orgId: String, spec: String)(implicit configuration: OrganizationConfiguration)

  def deleteOrphansBySpec(orgId: String, spec: String, startIndexing: DateTime)(implicit configuration: OrganizationConfiguration)

  def rollback(implicit configuration: OrganizationConfiguration)

}