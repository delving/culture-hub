package core.storage

import eu.delving.basex.client._
import eu.delving.basex.client.BaseX
import exceptions.StorageInsertionException
import org.basex.server.ClientSession
import java.io.ByteArrayInputStream
import xml.Node
import play.api.Logger

/**
 * BaseX-based Storage engine.
 *
 * One BaseX db == One collection
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */


class BaseXStorage(host: String, port: Int, ePort: Int, user: String, password: String) {

  lazy val storage = new BaseX(host, port, ePort, user, password, false)

  def createCollection(orgId: String, collectionName: String): Collection = {
    val c = BaseXCollection(orgId, collectionName)
    storage.createDatabase(c.storageName)
    c
  }

  def openCollection(c: Collection): Option[Collection] = openCollection(c.orgId, c.name)

  def openCollection(orgId: String, collectionName: String): Option[Collection] = {
    val c = BaseXCollection(orgId, collectionName)
    try {
      storage.openDatabase(c.storageName)
      Some(c)
    } catch {
      case _ => None
    }
  }

  def deleteCollection(c: Collection) {
    storage.dropDatabase(c.storageName)
  }

  def withSession[T](collection: Collection)(block: ClientSession => T) = {
    storage.withSession(collection.storageName) {
      session =>
        block(session)
    }
  }

  def withBulkSession[T](collection: Collection)(block: ClientSession => T) = {
    storage.withSession(collection.storageName) {
      session =>
        session.setAutoflush(false)
        block(session)
        session.setAutoflush(true)
    }
  }

  def store(collection: Collection, records: Iterator[Record], namespaces: Map[String, String], onRecordInserted: Long => Unit): Long = {
    var inserted: Long = 0
    withBulkSession(collection) {
      session =>
        val versions: Map[String, Int] = (session.find("""for $i in /*:record let $id := $i/@id group by $id return <version id="{$id}">{count($i)}</version>""") map {
          v: Node =>
            ((v \ "@id").text -> v.text.toInt)
        }).toMap

        val it = records.zipWithIndex
        while(it.hasNext) {
          val next = it.next()
          if(next._2 % 10000 == 0) session.flush()
          try {
            session.add(next._1.id, buildRecord(next._1, versions.get(next._1.id).getOrElse(0), namespaces, next._2))
          } catch {
            case t =>
              Logger("CultureHub").error(next._1.toString)
              throw new StorageInsertionException(next._1.toString, t)
          }
          inserted += 1
          onRecordInserted(inserted)
        }
        session.flush()
        session.createAttributeIndex()
      }
    inserted
  }

  def count(collection: BaseXCollection): Int = {
    val c = openCollection(collection)
    c.map {
      col =>
        withSession(col) {
          implicit session => count
        }
    }.getOrElse {
      0
    }
  }

  def buildRecord(record: Record, version: Int, namespaces: Map[String, String], index: Int) = {

    val ns = namespaces.map(ns => if(ns._1.isEmpty) """xmlns="%s"""".format(ns._2) else """xmlns:%s="%s"""".format(ns._1, ns._2)).mkString(" ")

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
    if(v.isEmpty) 0 else v.toInt
  }

  def count(implicit session: ClientSession): Int = {
    session.findOne("let $r := /*:record[@version = %s] return <count>{count($r)}</count>".format(currentCollectionVersion)).get.text.toInt
  }

  def findAllCurrent(implicit session: ClientSession) = {
    session.find("for $i in /*:record[@version = %s] order by $i/system/index return $i".format(currentCollectionVersion))
  }

}

case class BaseXCollection(orgId: String, name: String) extends Collection {
  override def storageName = orgId + "____" + name
}




