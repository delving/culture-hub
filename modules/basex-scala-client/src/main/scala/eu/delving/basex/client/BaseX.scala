package eu.delving.basex.client

import org.basex.BaseXServer
import java.io.{File, ByteArrayInputStream}
import org.basex.server.ClientSession
import org.basex.core.cmd.{Rename, Delete}
import xml.Node

/**
 * TODO support remote connection
 *
 * TODO mass insert, with bulk block
 * TODO query with limits
 *
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class BaseX(host: String, port: Int, user: String, pass: String) extends Implicits {

  private var server: BaseXServer = null

  /**
   * Starts an embedded BaseX server
   * @param dataDirectory the data directory on disk. Leave empty to use BaseX default.
   */
  def start(dataDirectory: Option[String] = None) {
    if(dataDirectory.isDefined) {
      val d = new File(dataDirectory.get)
      if(!d.exists()) {
        val created = d.mkdirs()
        if(!created) throw new RuntimeException("Failed to create data directory for BaseX " + dataDirectory)
      }
      System.setProperty("org.basex.path", d.getAbsolutePath)
    }
    server = new BaseXServer()
  }

  /**
   * Stops an embedded BaseX server
   */
  def stop() {
    BaseXServer.stop(port, 1985)
  }

  def withSession[T](block: ClientSession => T) = {
    val session = new ClientSession(host, port, user, pass)
    try {
      block(session)
    } finally {
      session.close()
    }
  }

  def withQuery[T](database: String, query: String)(block: RichClientQuery => T) = {
    withSession {
      session =>
        session.execute("open " + database)
        val q = session.query(query)
        try {
          block(q)
        } finally {
          q.close()
        }

    }
  }

  def createDatabase(name: String) {
    withSession {
      session => session.execute("create db " + name)
    }
  }

  def dropDatabase(name: String) {
    withSession {
      session => session.execute("drop db " + name)
    }
  }

  def add(database: String, path: String, document: String) {
    withSession {
      session =>
        session.execute("open " + database)
        session.add(path, new ByteArrayInputStream(document.getBytes("utf-8")))
    }
  }

  def replace(database: String, path: String, document: String) {
    withSession {
      session =>
        session.execute("open " + database)
        session.replace(path, new ByteArrayInputStream(document.getBytes("utf-8")))
    }
  }

  def rename(database: String, path: String, newPath: String) {
    withSession {
      session =>
        session.execute("open " + database)
        session.execute(new Rename(path, newPath))
    }
  }

  def delete(database: String, path: String) {
    withSession {
      session =>
        session.execute("open " + database)
        session.execute(new Delete(path))
    }
  }

  def query(database: String, query: String): List[String] = {
    withSession {
      session =>
        session.execute("open " + database)
        val q = session.query(query)
        val r = q.toList
        q.close()
        r
    }
  }

  def fetchRaw(database: String, path: String): Option[String] = {
    withSession {
      session => session.query("""db:open("%s", "%s")""".format(database, path)).toList.headOption
    }
  }

  def fetch(database: String, path: String): Option[Node] = fetchRaw(database, path).map(scala.xml.XML.loadString(_))

}