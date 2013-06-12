package ead

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import scala.xml.Source
import util.EADSimplifier
import play.api.test._
import play.api.test.Helpers._
import services.EADIndexingAnalysisService
import core.HubId
import eu.delving.schema.SchemaVersion

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class EADTest extends FlatSpec with ShouldMatchers {

  val ghaRecord = {
    val source = Source.fromInputStream(getClass.getClassLoader.getResourceAsStream("ghaRecord.xml"))
    scala.xml.XML.load(source)
  }

  val ghaRecordDom = {
    val source = Source.fromInputStream(getClass.getClassLoader.getResourceAsStream("ghaRecord.xml"))
    val builder = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
    builder.parse(source)
  }

  "The simplifier" should "properly simplify the top-level title" in {
    val simplified = EADSimplifier.simplify(ghaRecord)
    (simplified \ "title").text should be("Archieven van de notarissen residerende te Gouda, 1843-1925")
  }

  "The simplifier" should "properly simplify the ID of the first c" in {
    val simplified = EADSimplifier.simplify(ghaRecord)
    (simplified \ "node" \ "node" \ "title").head.text should be("Jaarlijkse klapper op de comparanten, 1819-1860")
  }

  "The EAD indexing analyser" should "find the right amount of documents" in {
    val analyzer = new EADIndexingAnalysisService
    val analysis = analyzer.analyze(HubId("delving", "GhaEAD", "0044"), new SchemaVersion("ead", "1.2.3"), ghaRecordDom)

    analysis.size should be(58)
  }

  "The EAD indexing analyser" should "properly analyze the root document" in {
    val analyzer = new EADIndexingAnalysisService
    val analysis = analyzer.analyze(HubId("delving", "GhaEAD", "0044"), new SchemaVersion("ead", "1.2.3"), ghaRecordDom)

    val root = analysis.find(r => r("delving_parentPath").head == "/").head
    root("delving_title").head should be("Archieven van de notarissen residerende te Gouda, 1843-1925")

  }

}
