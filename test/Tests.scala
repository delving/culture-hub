package test

import cake.MetadataModelComponent
import com.borachio.scalatest.MockFactory
import org.scalatest.matchers._
import play.test._
import util._
import models._
import org.scalatest.Suite
import eu.delving.metadata.{MetadataModelImpl, MetadataModel}

/**
 * General test environment. Wire-in components needed for tests here and initialize them with Mocks IF THEY ARE MOCKABLE (e.g. the ThemeHandler is not)
 */
trait TestEnvironment extends ThemeHandlerComponent with MetadataModelComponent with Suite with MockFactory {
  val metadataModel: MetadataModel = mock[MetadataModel]
  val themeHandler: ThemeHandler = new ThemeHandler // mock[ThemeHandler]
}

/**
 * Test for the ThemeHandler. We use UnitFlatSpec which is a Play version of the FlatSpec
 */
class ThemeHandlerTests extends UnitFlatSpec with ShouldMatchers with TestDataGeneric with TestEnvironment {

  override val themeHandler = new ThemeHandler

  it should "load themes from disk into the database" in {
    themeHandler.startup()
    themeHandler.hasSingleTheme should be(false)
    PortalTheme.findAll.size should not be (0)
  }

  it should "deliver a default theme" in {
    themeHandler.getDefaultTheme should not be (null)
  }

}

// TODO set-up solr startup for tests - via a script or so
//class DataSetSpec extends UnitFlatSpec with ShouldMatchers with TestDataGeneric with BeforeAndAfterAll {
//  import models.DataSet
//
//  val ds = DataSet.findBySpec("Verzetsmuseum").get
//
//  override def beforeAll() {
//    DataSet.deleteFromSolr(ds)
//  }
//
//  override def afterAll() {
//    DataSet.deleteFromSolr(ds)
//  }
//
//  it should "should Index every entry in the dataset" in {
//    DataSet.getRecords(ds).count() should equal (3)
//    val outputCount = DataSet.indexInSolr(ds, "icn")
//    outputCount should equal ((3, 0))
//  }
//}

class MappingEngineSpec extends UnitFlatSpec with ShouldMatchers with TestEnvironment {

  import eu.delving.sip.MappingEngine
  import io.Source

  override val metadataModel = {
    val metadataModelImpl = new MetadataModelImpl()
    metadataModelImpl.setFactDefinitionsFile(DataSet.getFactDefinitionFile)
    metadataModelImpl.setRecordDefinitionFiles(RecordDefinition.getRecordDefinitionFiles : _*)
    metadataModelImpl
  }

  val record =
            """<priref>6389</priref>
                <dimension.unit>cm</dimension.unit>
                <dimension.unit>cm</dimension.unit>
                <dimension.unit>cm</dimension.unit>
                <dimension.unit>cm</dimension.unit>
                <dimension.precision>2x</dimension.precision>
                <dimension.precision>2x</dimension.precision>
                <dimension.precision>2x</dimension.precision>
                <dimension.precision>2x</dimension.precision>
                <dimension.type>hoogte</dimension.type>
                <dimension.type>lengte</dimension.type>
                <dimension.type>hoogte</dimension.type>
                <dimension.type>lengte</dimension.type>
                <dimension.value>77</dimension.value>
                <dimension.value>54</dimension.value>
                <dimension.value>57</dimension.value>
                <dimension.value>65</dimension.value>
                <collection>toegepaste kunst</collection>
                <collection>stadsgeschiedenis</collection>
                <collection>onedele metalen</collection>
                <object_name>wandluster</object_name>
                <object_number>10000</object_number>
                <reproduction.reference>o108.jpg</reproduction.reference>
                <reproduction.identifier_URL>\\onedelemetalen\\o108.jpg</reproduction.identifier_URL>
                <techniek.vrije.tekst>ijzer, gesmeed, gegoten, verguld</techniek.vrije.tekst>
                <title>Vier wandlusters</title>
                <creator>Anoniem</creator>
                <creator.date_of_birth.start>?</creator.date_of_birth.start>
                <production.date.start>1780</production.date.start>
                <production.date.end>1799</production.date.end>
                <acquisition.method>schilder</acquisition.method>
                <acquisition.date>1947</acquisition.date>
                <association.subject/>
                <association.subject>bestuurders (Utrecht)</association.subject>
                <priref>6389</priref>
                """

  val mappingString = Source.fromInputStream(getClass.getResourceAsStream("/sample_icn_mapping.xml"), "utf-8").getLines().mkString

  import scala.collection.JavaConversions.asJavaMap
  val engine: MappingEngine = new MappingEngine(mappingString, asJavaMap(Map[String,String]()), play.Play.classloader, metadataModel)

  it should "should run over 1000 entries fast" in {
    for (i <- 0 to 10) {
      var now: Long = System.currentTimeMillis
      val doc = engine.executeMapping(record)
//      println(engine.toString)
      val total_time = System.currentTimeMillis() - now
      println("Mapping time per iteration: " + total_time)
//      println(doc.toString)
    }
    println(engine.toString)
  }

}

