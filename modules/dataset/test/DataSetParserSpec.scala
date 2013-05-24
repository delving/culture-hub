import collection.mutable
import collection.mutable.ListBuffer
import core.storage.Record
import java.io.ByteArrayInputStream
import models.DataSet
import org.specs2.execute.Error
import play.api.test._
import play.api.test.Helpers._
import util.SimpleDataSetParser

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class DataSetParserSpec extends BootstrapAwareSpec {

  step {
    loadStandalone(SAMPLE_A)
  }

  "The DataSetParser" should {

    "parse an input stream" in {

      withTestConfig {
        val buffer = parseStream()
        buffer.length must be equalTo (2)
      }

    }

    "properly assign invalid metadata formats" in {

      withTestConfig {
        val ds = DataSet.dao("delving").findBySpecAndOrgId(SAMPLE_A, "delving").get
        DataSet.dao("delving").getInvalidRecords(ds)("icn").contains(1) must equalTo(true)
      }

    }

    "preserve cdata" in {
      withTestConfig {
        val buffer = parseStream()
        buffer(0).document must contain("<![CDATA[")
      }
    }

    "preserve &amp; in elements" in {
      withTestConfig {
        val buffer = parseStream()

        val parsed = buffer(0).document
        parsed must contain("<urlWithAmp>http://www.inghist.nl/retroboeken/nnbw?source=10&amp;page_number=1</urlWithAmp>")
      }
    }

    "preserve &amp; in attributes" in {
      withTestConfig {
        val buffer = parseStream()

        val parsed = buffer(0).document
        parsed must contain("<attrWithAmp some=\"http://www.inghist.nl/retroboeken/nnbw?source=10&amp;page_number=1\">Value</attrWithAmp>")
      }
    }

    "propertly escape identifiers with non-valid XML characters in them" in {
      withTestConfig {
        val buffer = parseStream(sampleSourceWithWeirdId)

        val parsed = buffer(0)
        buffer.size must equalTo(1)
        parsed.id must equalTo("http://digitaltmuseum.no/artifactView.do?idOwner=KFS&amp;idIdentifier")
      }
    }

  }

  step(cleanup())

  def parseStream(): mutable.Buffer[Record] = parseStream(sampleSource)

  def parseStream(sampleSource: String): mutable.Buffer[Record] = {
    val ds = DataSet.dao("delving").findBySpecAndOrgId(SAMPLE_A, "delving").get
    val bis = new ByteArrayInputStream(sampleSource.getBytes)
    val parser = new SimpleDataSetParser(bis, ds)

    val buffer = ListBuffer[Record]()

    try {

      while (parser.hasNext) {
        val record = parser.next()
        buffer.append(record)
      }

    } catch {
      case t: Throwable =>
        t.printStackTrace()
        Error(t)
    }

    buffer
  }

  val sampleSource =
    """<?xml version='1.0' encoding='UTF-8'?>
<delving-sip-source xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" >
<input id="1">
<priref>1</priref>
<someDescription><![CDATA[&notAnEntity;]]></someDescription>
<edit.source>collect>intern</edit.source>
<edit.source>photo</edit.source>
<edit.source>collect>intern</edit.source>
<input.source>FileMakerPro</input.source>
<edit.time>14:54:55</edit.time>
<edit.time>12:37:39</edit.time>
<edit.time>16:05:56</edit.time>
<creator.history />
<edit.name>t.kamerling</edit.name>
<edit.name>alina</edit.name>
<edit.name>Daniel</edit.name>
<input.name>Conversie AIS</input.name>
<creator.date_of_death />
<creator.date_of_birth />
<edit.date>2011-03-15</edit.date>
<edit.date>2009-02-19</edit.date>
<edit.date>2006-11-22</edit.date>
<input.date>2005-01-14</input.date>
<dimension.value>24.3</dimension.value>
<dimension.value>2.6</dimension.value>
<insurance.value>700.00</insurance.value>
<acquisition.method>aankoop</acquisition.method>
<creator>onbekend</creator>
<acquisition.price.value>42</acquisition.price.value>
<production.place>Engeland</production.place>
<acquisition.source>Foo Bar</acquisition.source>
<acquisition.price.currency>BigMac</acquisition.price.currency>
<insurance.value.currency>BigMac</insurance.value.currency>
<technique>gevormd</technique>
<technique>koperdruk</technique>
<technique>loodglazuur</technique>
<title>Bord met zwart decor van cartouche met opschrift</title>
<condition.notes>schilfers van de rand, ingetrokken vuil.</condition.notes>
<technique.part>FORM</technique.part>
<technique.part>DEC</technique.part>
<technique.part>FIN</technique.part>
<location>1.12-K10-P05</location>
<reproduction.creator />
<reproduction.type />
<reproduction.date />
<number_of_parts>1</number_of_parts>
<acquisition.place>Zutphen</acquisition.place>
<phys_characteristic.keyword>cartouche</phys_characteristic.keyword>
<phys_characteristic.keyword>opschrift</phys_characteristic.keyword>
<phys_characteristic.aspect>creamware</phys_characteristic.aspect>
<title.type>toegekend</title.type>
<object_name>bord</object_name>
<location.default>1.12-K10-P05</location.default>
<material.notes>scherf cr?me</material.notes>
<material>hard aardewerk</material>
<location_type>DEP</location_type>
<location_check.date>2007-12-04</location_check.date>
<location_check.name>AD</location_check.name>
<object_number>OKS 1989-001</object_number>
<inscription.notes>geen</inscription.notes>
<institution.code>0061</institution.code>
<current_owner>Ottema-Kingma Stichting</current_owner>
<creator.role>vervaardiger</creator.role>
<reproduction.reference>OKS 1989-001 [01]</reproduction.reference>
<reproduction.format />
<reproduction.web_exclusion />
<dimension.unit>cm</dimension.unit>
<dimension.unit>cm</dimension.unit>
<production.date.start>1791</production.date.start>
<production.date.notes>gedateerd</production.date.notes>
<dimension.type>diameter</dimension.type>
<dimension.type>hoogte</dimension.type>
<production.date.end>1791</production.date.end>
<institution.place>Leeuwarden</institution.place>
<description>De geschulpte rand is voorzien van een versiering in zwart in de vorm van bloemtakken. Op het verdiepte middenvlak is een in Louis XV stijl cartouche aangebracht met tekst.(Ingekorte beschrijving).</description>
<description>Opschrift: Welvaart; Tot desen Huijsen Van; Yan en Yoanna Ver Boom; Ao 1797 den 20. Meij</description>
<institution.name>Princessehof Leeuwarden</institution.name>
<reproduction.identifier_URL>..\..\..\Images PH\OKS 1989-001 [01].jpg</reproduction.identifier_URL>
<administration_name>Europa</administration_name>
<percentf>1791</percentf>
<percentH>creamware</percentH>
<percentD>2.6</percentD>
<percentC>24.3</percentC>
<urlWithAmp>http://www.inghist.nl/retroboeken/nnbw?source=10&amp;page_number=1</urlWithAmp>
<attrWithAmp some="http://www.inghist.nl/retroboeken/nnbw?source=10&amp;page_number=1">Value</attrWithAmp>
</input>
<input id="2">
<priref>2</priref>
<edit.source>collect>intern</edit.source>
<edit.source>photo</edit.source>
<edit.source>collect>intern</edit.source>
<edit.source>collect>intern</edit.source>
<input.source>FileMakerPro</input.source>
<edit.time>14:54:58</edit.time>
<edit.time>14:43:06</edit.time>
<edit.time>12:52:17</edit.time>
<edit.time>16:05:59</edit.time>
<creator.history />
<creator.history />
<edit.name>t.kamerling</edit.name>
<edit.name>alina</edit.name>
<edit.name>peter</edit.name>
<edit.name>Daniel</edit.name>
<input.name>Conversie AIS</input.name>
<creator.date_of_death />
<creator.date_of_death>0</creator.date_of_death>
<creator.date_of_birth />
<creator.date_of_birth>0</creator.date_of_birth>
<edit.date>2011-03-15</edit.date>
<edit.date>2009-01-14</edit.date>
<edit.date>2007-01-25</edit.date>
<edit.date>2006-11-22</edit.date>
<input.date>2005-01-14</input.date>
<dimension.value>32.5</dimension.value>
<dimension.value>4.0</dimension.value>
<insurance.value>5000.00</insurance.value>
<acquisition.method>aankoop</acquisition.method>
<creator>Tichelaar, gleibakkerij van de familie (1700-1868)</creator>
<creator>Hofstra, Douwe Klazes</creator>
<acquisition.price.value>42</acquisition.price.value>
<production.place>Makkum</production.place>
<acquisition.source>Foo Bar</acquisition.source>
<acquisition.price.currency>BigMac</acquisition.price.currency>
<insurance.value.currency>BigMac</insurance.value.currency>
<technique>gedraaid</technique>
<technique>inglazuurschildering</technique>
<technique>tinglazuur</technique>
<title>Schotel met blauwwit bijbels decor</title>
<condition.notes>schilfers, kleine randrestauratie, midden rechts.</condition.notes>
<technique.part>FORM</technique.part>
<technique.part>DEC</technique.part>
<technique.part>FIN</technique.part>
<location>0.13-V09-P02</location>
<reproduction.creator />
<reproduction.type />
<reproduction.date />
<number_of_parts>1</number_of_parts>
<acquisition.place>Zutphen</acquisition.place>
<phys_characteristic.keyword>bijbels decor</phys_characteristic.keyword>
<phys_characteristic.keyword>opschrift</phys_characteristic.keyword>
<phys_characteristic.aspect>faience</phys_characteristic.aspect>
<title.type>toegekend</title.type>
<object_name>schotel</object_name>
<location.default>0.13-V09-P02</location.default>
<material>aardewerk</material>
<location_type>VAST</location_type>
<location_check.date>2007-11-15</location_check.date>
<location_check.name>AD</location_check.name>
<object_number>OKS 1989-002</object_number>
<inscription.notes>geen</inscription.notes>
<institution.code>0061</institution.code>
<current_owner>Ottema-Kingma Stichting</current_owner>
<creator.role>vervaardiger</creator.role>
<creator.role>schilder</creator.role>
<reproduction.reference>OKS 1989-002 [01]</reproduction.reference>
<reproduction.format />
<reproduction.web_exclusion />
<dimension.unit>cm</dimension.unit>
<dimension.unit>cm</dimension.unit>
<production.date.start>1790</production.date.start>
<dimension.type>diameter</dimension.type>
<dimension.type>hoogte</dimension.type>
<production.date.end>1800</production.date.end>
<institution.place>Leeuwarden</institution.place>
<description>Iets verdiept liggende bodem, zeer weinig geprononceerde standring, trapsgewijs spreidende wand, gele scherf, grijs/wit tinglazuur, in blauw gedecoreerd, rondom een bloemguirlande, in vieren, op het plat een voorstelling van Judas' verraad van Jezus Opschrift: Mat. 26 vers 47 (En als Hij nog sprak, ziet, Judas, een van de twaalven, kwam, en met hem een grote schare, met zwaarden en stokken, gezonden van de overpriesters en ouderlingen des volks).</description>
<institution.name>Princessehof Leeuwarden</institution.name>
<reproduction.identifier_URL>..\..\..\Images PH\OKS 1989-002 [01].JPG</reproduction.identifier_URL>
<administration_name>Europa</administration_name>
<percentf>ca 1790</percentf>
<percentG>Makkum</percentG>
<percentD>4</percentD>
<percentC>32.5</percentC>
</input>
</delving-sip-source>"""

  val sampleSourceWithWeirdId = """<?xml version='1.0' encoding='UTF-8'?>
<delving-sip-source xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" >
<input id="http://digitaltmuseum.no/artifactView.do?idOwner=KFS&amp;idIdentifier">
<priref>1</priref>
</input>
</delving-sip-source>"""

}