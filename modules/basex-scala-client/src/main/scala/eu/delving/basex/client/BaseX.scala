package eu.delving.basex.client

import org.basex.BaseXServer
import java.io.{File, ByteArrayInputStream}
import org.basex.server.ClientSession

/**
 * TODO start & stop hooks. explicit startup method, rather than by constructor (booh)
 *
 * TODO remove
 * TODO replace
 *
 * TODO mass insert, with bulk block
 * TODO query with limits
 *
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class BaseX(host: String, port: Int, user: String, pass: String, dataDirectory: Option[String] = None) extends Implicits {

  if(dataDirectory.isDefined) {
    val d = new File(dataDirectory.get)
    if(!d.exists()) {
      val created = d.mkdirs()
      if(!created) throw new RuntimeException("Failed to create data directory for BaseX " + dataDirectory)
    }
    System.setProperty("org.basex.path", d.getAbsolutePath)
  }

  val server: BaseXServer = new BaseXServer()

  def shutdown() {
    server.stop()
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

  def add(database: String, path: String, document: String) {
    withSession {
      session =>
        val c = session.execute("open " + database)
        println(c)
        session.add(path, new ByteArrayInputStream(document.getBytes("utf-8")))
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

}