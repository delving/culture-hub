package eu.delving.basex.client

import org.basex.core.BaseXException
import org.specs2.mutable.Specification

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

  "the BaseX storage" should {

    "create a database" in {
      s.createDatabase("test")
    }

    "insert a document" in {
      s.add("test", "foo.xml", "<root><bla>bar</bla></root>")
      val r = s.query("test", "//root")
      r.size must be equalTo (1)
    }

    "replace a document" in {
      s.replace("test", "foo.xml", "<replacedRoot><bla>bar</bla></replacedRoot>")

      val r = s.query("test", "//replacedRoot")
      val r1 = s.query("test", "//root")

      r.size must be equalTo (1)
      r1.size must be equalTo (0)
    }

    "delete a document" in {
      s.delete("test", "foo.xml")
      val r = s.query("test", "//replacedRoot")
      r.size must be equalTo (0)
    }

    "fail to insert a document in a non-existing db" in {
      s.add("blablabla", "foo.xml", "<root/>") must throwA[BaseXException]
    }

  }

  step {
    s.dropDatabase("test")
    s.stop()
  }

}
