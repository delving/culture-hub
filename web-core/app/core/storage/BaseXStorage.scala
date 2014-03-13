package core.storage

import eu.delving.basex.client._
import eu.delving.basex.client.BaseX
import exceptions.StorageInsertionException
import org.basex.server.ClientSession
import java.io.ByteArrayInputStream
import play.api.Logger
import core.collection.Collection
import models.BaseXConfiguration

/**
 * BaseX-based Storage engine.
 *
 * One BaseX db == One collection
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class BaseXStorage(configuration: BaseXConfiguration) {

  val DEFAUL_BASEX_PATH_EXTENSION = "DOT"

  lazy val storage = new BaseX(
    configuration.host,
    configuration.port,
    configuration.eport,
    configuration.user,
    configuration.password,
    false
  )

  def createCollection(collection: Collection, prefix: Option[String]): Collection = {
    storage.createDatabase(storageName(collection, prefix))
    collection
  }

  def renameCollection(collection: Collection, prefix: Option[String], newName: String) {
    storage.alter(storageName(collection, prefix), storageName(collection, newName))
  }

  def openCollection(collection: Collection, prefix: Option[String]): Option[Collection] = {
    try {
      storage.openDatabase(storageName(collection, prefix))
      Some(collection)
    } catch {
      case t: Throwable => None
    }
  }

  def deleteCollection(collection: Collection, prefix: Option[String]) {
    storage.dropDatabase(storageName(collection, prefix))
  }

  def withSession[T](collection: Collection, prefix: Option[String])(block: ClientSession => T) = {
    storage.withSession(storageName(collection, prefix)) {
      session =>
        block(session)
    }
  }

  def withBulkSession[T](collection: Collection, prefix: Option[String])(block: ClientSession => T) = {
    storage.withSession(storageName(collection, prefix)) {
      session =>
        session.setAutoflush(flush = false)
        block(session)
        session.setAutoflush(flush = true)
    }
  }

  def store(collection: Collection, prefix: Option[String], records: Iterator[Record], namespaces: Map[String, String], onRecordInserted: Long => Unit): Long = {
    var inserted: Long = 0
    val start = System.currentTimeMillis()

    withBulkSession(collection, prefix) {
      session =>

        val it = records.zipWithIndex
        while (it.hasNext) {
          val next = it.next()
          if (next._2 % 10000 == 0) session.flush()
          try {
            // we add the record on a path of its own, which is useful because then we know how many distinct records are stored in a BaseX collection
            // given that those path need to be valid file names, we do preemptive sanitization here
            val sanitizedId = next._1.id.replaceAll("[:;\\./]", "_")
            session.add(sanitizedId, buildRecord(next._1, 0, namespaces, next._2))
          } catch {
            case t: Throwable =>
              Logger("CultureHub").error(next._1.toString)
              throw new StorageInsertionException(next._1.toString, t)
          }
          inserted += 1
          onRecordInserted(inserted)
        }
        session.flush()
        session.createAttributeIndex()
    }
    val end = System.currentTimeMillis()
    Logger("CultureHub").info("Storing %s records into BaseX took %s ms".format(inserted, end - start))
    inserted
  }

  def count(collection: Collection, prefix: Option[String]): Int = {
    val c = openCollection(collection, prefix)
    c.map {
      col =>
        withSession(col, prefix) {
          implicit session => count
        }
    }.getOrElse {
      0
    }
  }

  def buildRecord(record: Record, version: Int, namespaces: Map[String, String], index: Int) = {

    val ns = util.XMLUtils.namespacesToString(namespaces)

    new ByteArrayInputStream("""<record id="%s" version="%s" %s>
      <system>
        <schemaPrefix>%s</schemaPrefix>
        <index>%s</index>
      </system>
      <document>%s</document>
      <links/>
    </record>""".format(record.id, version, ns, record.schemaPrefix, index, record.document).getBytes("utf-8"))
  }

  // ~~~ Collection queries

  def currentCollectionVersion(implicit session: ClientSession): Int = {
    val v = session.findOne("let $r := /*:record return <currentCollectionVersion>{max($r/@version)}</currentCollectionVersion>").get.text
    if (v.isEmpty) 0 else v.toInt
  }

  def count(implicit session: ClientSession): Int = {
    session.findOne("let $r := /*:record[@version = %s] return <count>{count($r)}</count>".format(currentCollectionVersion)).get.text.toInt
  }

  def findAllCurrent(implicit session: ClientSession) = {
    session.find("for $i in /*:record[@version = %s] order by $i/system/index return $i".format(currentCollectionVersion))
  }

  def findAllCurrentDocuments(implicit session: ClientSession) = {
    val currentVersion = currentCollectionVersion
    session.execute("SET SERIALIZER omit-xml-declaration=yes,method=xml,indent=yes,indents=0,format=yes")
    session.findRaw("for $i in /*:record[@version = %s] order by number($i/system/index) return $i/*:document/*:input".format(currentVersion))
  }

  private def storageName(c: Collection, newName: String) = s"${c.getOwner}____$newName"

  private def storageName(c: Collection, prefix: Option[String]) = {
    prefix match {
      case Some(ext) =>
        s"${c.getOwner}____${c.spec}__$ext"
      case None =>
        s"${c.getOwner}____${c.spec}"
    }
  }

}