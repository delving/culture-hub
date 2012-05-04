package eu.delving.basex.client

import org.basex.core.BaseXException
import org.specs2.mutable.Specification

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class BaseXSpec extends Specification {

  var s: BaseX = null

  step {
    s = new BaseX("localhost", 1984, "admin", "admin")
  }

  "the BaseX storage" should {

    "insert a document" in {

      s.createDatabase("test")
      s.add("test", "foo.xml", "<root><bla>bar</bla></root>")

      val r = s.query("test", "//root")

      r.size must be equalTo (1)
    }

    "fail to insert a document in a non-existing db" in {
      s.add("blablabla", "foo.xml", "<root/>") must throwA[BaseXException]
    }

  }

  step(s.shutdown())

}
