package eu.delving.basex.client

import org.basex.server.ClientQuery

object `package` extends Implicits

trait Implicits {

  class RichClientQuery(query: ClientQuery) extends Iterator[String] {
    def hasNext: Boolean = query.more()
    def next(): String = query.next()
  }

  implicit def withRichClientQuery[A <: ClientQuery](query: A): RichClientQuery = new RichClientQuery(query)
}