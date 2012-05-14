package eu.delving.basex.client

import org.basex.server.{ClientSession, ClientQuery}
import org.basex.core.cmd.Flush
import xml.{XML, Node}


object `package` extends Implicits

trait Implicits {

  class RichClientQuery(query: ClientQuery) extends Iterator[String] {
    def hasNext: Boolean = query.more()
    def next(): String = query.next()
  }

  class RichClientSession(session: ClientSession) {

    def open(db: String) {
      session.execute("open " + db)
    }

    def find(query: String): Iterator[Node] = {
      session.query(query).map(XML.loadString(_))
    }

    def findRaw(query: String): Iterator[String] = {
      session.query(query)
    }

    def findOne(query: String): Option[Node] = {
      findOneRaw(query).map(XML.loadString(_)).toList.headOption
    }

    def findOneRaw(query: String): Option[String] = {
      session.query(query).toList.headOption
    }

    def setAutoflush(flush: Boolean) {
      if(flush) {
        session.execute("set autoflush true")
      } else {
        session.execute("set autoflush false")
      }
    }

    def flush() {
      session.execute(new Flush())
    }

    def createAttributeIndex() {
      session.execute("create index attribute")
    }

  }

  implicit def withRichClientQuery[A <: ClientQuery](query: A): RichClientQuery = new RichClientQuery(query)
  implicit def withRichClientSession[A <: ClientSession](session: A): RichClientSession = new RichClientSession(session)
}