package ead

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import scala.xml.Source
import util.EADSimplifier
import play.api.test._
import play.api.test.Helpers._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class EADTest extends FlatSpec with ShouldMatchers {

  "the simplifier" should "simplify" in {

    Option(getClass.getClassLoader.getResourceAsStream("apeEAD_SE_KrA_0058.xml")) map {
      resourceStream =>
        {
          val source = Source.fromInputStream(resourceStream)
          val xml = scala.xml.XML.load(source)
          EADSimplifier.simplify(xml)
        }
    }

  }

}
