/*
 * Copyright 2012 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import eu.delving.templates.Play2VirtualFile
import io.Source
import java.io.File
import models.GrantType
import org.specs2.mutable._
import play.api.i18n.Lang
import play.api.Play
import play.api.Play.current
import play.api.test._
import play.api.test.Helpers._
import play.templates.GenericTemplateLoader
import core.rendering._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class ViewRenderSpec extends Specification {

  "The ViewRenderer" should {

    "render a record as HTML" in {
      running(FakeApplication()) {

        val namespaces = Map("delving" -> "http://www.delving.eu/schemas/delving-1.0.xsd", "dc" -> "http://dublincore.org/schemas/xmls/qdc/dc.xsd", "icn" -> "http://www.icn.nl/schemas/ICN-V3.2.xsd")

        val renderer = new ViewRenderer("icn", "full")
        val view = renderer.renderRecordWithView("icn", "full", testHtmlViewDefinition, testRecord(), List(GrantType("administrator", "blabla", "icn")), namespaces, Lang("en"))

        RenderNode.visit(view.viewTree)

        println()
        println()

        val template = GenericTemplateLoader.load(Play2VirtualFile.fromFile(Play.getFile("test/view.html")))
        val args: java.util.Map[String, Object] = new java.util.HashMap[String, Object]()
        args.put("view", view.viewTree)
        args.put("lang", "en")
        val rendered: String = template.render(args)
        val expected: String =
"""<div class="row">
<div class="section" id="description">
<div class="field">Description: This is a test record</div>
</div>
<div class="section" id="fields">
    <div>A test hierarchical record, Wood</div>
<div class="field">Purchase Price: 5000</div>
<div class="field">metadata.icn.purchaseType: auction</div>
<div class="link"><a href="http://foo.bar.com">Blablabla"></a></div>
</div>
<div class="section" id="complexFields">
<div class="field">metadata.icn.placeName: Paris</div>
<div class="field">metadata.icn.placeName: Berlin</div>
<div class="field">metadata.icn.placeName: Amsterdam</div>
</div>
</div>
"""

        println(rendered)

        rendered must be equalTo(expected)
      }
    }

    "render an AFF record as HTML" in {
      running(FakeApplication()) {

        val namespaces = Map("aff" -> "http://schemas.delving.eu/aff/aff_1.0.xsd")

        val affTestRecord = Source.fromFile(new File(Play.application.path, "test/resource/aff-example.xml")).getLines().mkString("\n")

        val renderer = new ViewRenderer("aff", "full")
        val view = renderer.renderRecord(affTestRecord, List.empty[GrantType], namespaces, Lang("en"))

        val template = GenericTemplateLoader.load(Play2VirtualFile.fromFile(Play.getFile("test/view.html")))
        val args: java.util.Map[String, Object] = new java.util.HashMap[String, Object]()
        args.put("view", view.viewTree)
        args.put("lang", "en")
        val rendered: String = template.render(args)

        RenderNode.visit(view.viewTree)

        println()
        println()

        println(rendered)

        1 must equalTo(1)
      }

    }

    "render a record as XML" in {

      val namespaces = Map("delving" -> "http://www.delving.eu/schemas/delving-1.0.xsd", "dc" -> "http://dublincore.org/schemas/xmls/qdc/dc.xsd", "icn" -> "http://www.icn.nl/schemas/ICN-V3.2.xsd")

      val view = ViewRenderer.fromDefinition("aff", "full").get.renderRecordWithView("aff", "full", testXmlViewDefinition, testRecord(), List.empty, namespaces, Lang("en"))

      val xml = view.toXmlString

      println(xml)
      println()

      val expected =
"""<?xml version="1.0" encoding="iso-8859-1" ?>
 <record xmlns:delving="http://www.delving.eu/schemas/delving-1.0.xsd" xmlns:dc="http://dublincore.org/schemas/xmls/qdc/dc.xsd">
   <item id="42">
      <dc:title>A test hierarchical record</dc:title>
      <delving:description>This is a test record<delving:description>
      <places>
          <place geo:country="France">
               <name>Paris</name>
          </place>
          <place geo:country="Germany">
               <name>Berlin</name>
          </place>
          <place geo:country="Netherlands">
               <name>Amsterdam</name>
          </place>
      </places>
   </item>
 </record>"""

      xml must equalTo(expected)

    }


    "render a record as JSON" in {

      val namespaces = Map("delving" -> "http://www.delving.eu/schemas/delving-1.0.xsd", "dc" -> "http://dublincore.org/schemas/xmls/qdc/dc.xsd", "icn" -> "http://www.icn.nl/schemas/ICN-V3.2.xsd")
      val renderer = new ViewRenderer("aff", "xml")
      val view = renderer.renderRecordWithView("aff", "xml", testXmlViewDefinition, testRecord(), List.empty, namespaces, Lang("en"))
      val json = view.toJson

      println(json)
      println()

      val expected = """{"record":{"item":{"places":[{"place":{"name":"Paris"}},{"place":{"name":"Berlin"}},{"place":{"name":"Amsterdam"}}],"dc:title":"A test hierarchical record","delving:description":"This is a test record"}}}"""

      json must equalTo (expected)

    }



    "render a legacy record as XML" in {
      running(FakeApplication()) {

        val namespaces = Map(
          "dc" -> "http://purl.org/dc/elements/1.1/",
          "ese" -> "http://www.europeana.eu/schemas/ese/",
          "dcterms" -> "http://purl.org/dc/termes/",
          "europeana" -> "http://www.europeana.eu/schemas/ese/",
          "delving" -> "http://www.delving.eu/schemas/",
          "tib" -> "http://www.thuisinbrabant.nl/namespace"
        )

        // wrap legacy record (as it would be in the mongo cache) in a root element with namespaces

        val testRecord = "<root %s>%s</root>".format(namespaces.map(ns => "xmlns:" + ns._1 + "=\"" + ns._2 + "\"").mkString(" "), legacyRecord())

        val renderer = new ViewRenderer("tib", "full")
        val view = renderer.renderRecord(testRecord, List.empty, namespaces, Lang("en"))

        println(view.toXmlString)
        println()

        1 must equalTo(1)

      }

    }



  }








  private def testHtmlViewDefinition =
    <view name="full">
      <row>
        <section id="description">
            <field path="/record/delving:summaryFields/delving:description" label="metadata.dc.description"/>
        </section>
        <section id="fields">
            <enumeration type="concatenated" separator=", " label="random" path="/record/delving:summaryFields/delving:title, /record/icn:data/icn:general/icn:material"/>
            <field path="/record/icn:data/icn:acquisition/icn:cost" label="metadata.icn.purchasePrice" role="administrator, own"/>
            <field path="/record/icn:data/icn:acquisition/@type" label="metadata.icn.purchaseType"/>
            <link url="/record/dc:data/dc:link" text="/record/dc:data/dc:name" />
        </section>
        <section id="complexFields">
             <list path="/record/icn:places/icn:place">
                 <field path="@name" label="metadata.icn.placeName"/>
             </list>
         </section>
      </row>
    </view>

  private def testXmlViewDefinition =
    <view name="xml">
      <elem name="record">
        <attrs>
          <attr prefix="xmlns" name="delving" value="http://www.delving.eu/schemas/delving-1.0.xsd" />
          <attr prefix="xmlns" name="dc" value="http://dublincore.org/schemas/xmls/qdc/dc.xsd" />
        </attrs>
        <elem name="item">
          <attrs>
            <attr name="id" value="42" />
          </attrs>
          <elem name="title" prefix="dc" expr="/record/delving:summaryFields/delving:title" />
          <elem name="description" prefix="delving" expr="/record/delving:summaryFields/delving:description" />
          <list name="places" path="/record/icn:places/icn:place">
              <elem name="place">
                <attrs>
                  <attr name="country" prefix="geo" expr="@country" />
                </attrs>
                <elem name="name" expr="@name" />
              </elem>
          </list>
        </elem>
      </elem>
    </view>


  private def testRecord(): String = {

    // test record, hierarchical
      """<?xml version="1.0" encoding="iso-8859-1" ?>
      <record xmlns:delving="http://www.delving.eu/schemas/delving-1.0.xsd" xmlns:dc="http://dublincore.org/schemas/xmls/qdc/dc.xsd" xmlns:icn="http://www.icn.nl/schemas/ICN-V3.2.xsd">
        <delving:summaryFields>
          <delving:title>A test hierarchical record</delving:title>
          <delving:description>This is a test record</delving:description>
          <delving:creator>John Lennon</delving:creator>
          <delving:owner>Museum of Music</delving:owner>
        </delving:summaryFields>
        <dc:data>
          <dc:type>picture</dc:type>
          <dc:name>Blablabla</dc:name>
          <dc:link>http://foo.bar.com</dc:link>
        </dc:data>
        <icn:data>
          <icn:general>
            <icn:material>Wood</icn:material>
            <icn:technique>Carving</icn:technique>
          </icn:general>
          <icn:acquisition type="auction">
            <icn:cost>5000</icn:cost>
          </icn:acquisition>
        </icn:data>
        <icn:places>
          <icn:place name="Paris" country="France" />
          <icn:place name="Berlin" country="Germany" />
          <icn:place name="Amsterdam" country="Netherlands" />
        </icn:places>
      </record>"""

  }
  
  private def legacyRecord(): String =
  """<dc:creator>C.</dc:creator>
  <dc:date>1965</dc:date>
  <dc:format>application/pdf</dc:format>
  <dc:publisher>De Brabantse Leeuw</dc:publisher>
  <dc:title>GESLACHT RULO</dc:title>
  <dcterms:hasVersion>JAARGANG XIV 1 9 6 5</dcterms:hasVersion>
  <delving:allFormats>raw</delving:allFormats>
  <delving:allFormats>tib</delving:allFormats>
  <delving:currentFormat>tib</delving:currentFormat>
  <delving:hasDigitalObject>true</delving:hasDigitalObject>
  <delving:hubId>thuisinbrabant_de-brabantse-leeuw_3967</delving:hubId>
  <delving:landingPage>
  http://www.thuisinbrabant.nl/de-brabantse-leeuw/817
  </delving:landingPage>
  <delving:orgId>thuisinbrabant</delving:orgId>
  <delving:pmhId>de-brabantse-leeuw_4ef0fdfb0cf21d42ad667346</delving:pmhId>
  <delving:publicFormats>raw</delving:publicFormats>
  <delving:recordType>mdr</delving:recordType>
  <delving:spec>de-brabantse-leeuw</delving:spec>
  <delving:thumbnail>
  http://thuisinbrabant.delving.org/thumbnail/thuisinbrabant/de-brabantse-leeuw/brabants_leeuw_1965_1_87_96/500
  </delving:thumbnail>
  <delving:visibility>10</delving:visibility>
  <delving:year>1965</delving:year>
  <europeana:collectionName>de-brabantse-leeuw</europeana:collectionName>
  <europeana:collectionTitle>De Brabantse Leeuw</europeana:collectionTitle>
  <europeana:country>netherlands</europeana:country>
  <europeana:dataProvider>De Brabantse Leeuw</europeana:dataProvider>
  <europeana:isShownAt>
  http://www.thuisinbrabant.nl/de-brabantse-leeuw/817
  </europeana:isShownAt>
  <europeana:isShownBy>
  http://thuisinbrabant.delving.org/pdf/thuisinbrabant/de-brabantse-leeuw/brabants_leeuw_1965_1_87_96.pdf
  </europeana:isShownBy>
  <europeana:language>nl</europeana:language>
  <europeana:provider>Erfgoed Brabant</europeana:provider>
  <europeana:rights>http://creativecommons.org/publicdomain/mark/1.0/</europeana:rights>
  <europeana:type>TEXT</europeana:type>
  <europeana:uri>de-brabantse-leeuw/817</europeana:uri>
  <tib:citName>ccBrabant_deBrabantseLeeuw</tib:citName>
  <tib:citOldId>ccBrabant_deBrabantseLeeuw_3967</tib:citOldId>
  <tib:collection>De Brabantse Leeuw</tib:collection>
  <tib:objectSoort>tijdschriftartikel</tib:objectSoort>
  <tib:pageEnd>96</tib:pageEnd>
  <tib:pageStart>87</tib:pageStart>
  <tib:thumbLarge>
  http://thuisinbrabant.delving.org/thumbnail/thuisinbrabant/de-brabantse-leeuw/brabants_leeuw_1965_1_87_96/500
  </tib:thumbLarge>
  <tib:thumbSmall>
  http://thuisinbrabant.delving.org/thumbnail/thuisinbrabant/de-brabantse-leeuw/brabants_leeuw_1965_1_87_96/180
  </tib:thumbSmall>"""

}