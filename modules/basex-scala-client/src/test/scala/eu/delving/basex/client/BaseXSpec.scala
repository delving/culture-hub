package eu.delving.basex.client

import org.basex.core.BaseXException
import org.specs2.mutable._
import scala.xml.Utility.trim

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class BaseXSpec extends Specification {

  var s: BaseX = {
    val server = new BaseX("localhost", 1984, "admin", "admin")
    server.start()
    server
  }

  sequential

  "the BaseX storage" should {

    "create a database" in {
      s.createDatabase("test")
      success
    }

    "open a database" in {
      s.openDatabase("test")
      success
    }

    "not open a non-existing database" in {
      s.openDatabase("gumby") must throwA[BaseXException]
    }

    "insert a document" in {
      s.add("test", "/foo.xml", "<root><bla>bar</bla></root>")
      val r = s.query("test", "//root")
      r.size must be equalTo (1)
    }

    "fetch a document as scala node" in {
      val r = s.fetch("test", "/foo.xml")
      r must be not empty
      trim(r.get) must be equalTo trim(<root><bla>bar</bla></root>)
    }

    "find something and return it as scala nodes" in {
      val r = s.withSession {
        session =>
          session.open("test")
          session.find("let $items := /root for $i in $items return <version id=\"{$i/@id}\">{count($i)}</version>").toList
      }
      r.size must equalTo (1)
    }

    "replace a document" in {
      s.replace("test", "/foo.xml", "<replacedRoot><bla>bar</bla></replacedRoot>")

      val r = s.query("test", "//replacedRoot")
      val r1 = s.query("test", "//root")

      r.size must be equalTo (1)
      r1.size must be equalTo (0)
    }

    "rename a document" in {
      s.rename("test", "/foo.xml", "/foo/foobar.xml")

      val r = s.query("test", "db:open(\"test\", \"/foo/foobar.xml\")")
      val r1 = s.query("test", "db:open(\"test\", \"/foo.xml\")")

      r.size must be equalTo (1)
      r1.size must be equalTo (0)
    }


    "delete a document" in {
      s.delete("test", "/foo/foobar.xml")
      val r = s.query("test", "//replacedRoot")
      r.size must be equalTo (0)
    }

    "fail to insert a document in a non-existing db" in {
      s.add("blablabla", "foo.xml", "<root/>") must throwA[BaseXException]
    }

    "shut down" in {
      s.dropDatabase("test")
      s.stop()
      success
    }

  }

}
