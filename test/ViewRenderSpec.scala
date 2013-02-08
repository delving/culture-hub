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
import models.Role
import org.specs2.mutable._
import play.api.i18n.Lang
import play.api.Play
import play.api.Play.current
import play.api.test._
import play.api.test.Helpers._
import play.templates.GenericTemplateLoader
import core.rendering._
import util.OrganizationConfigurationHandler

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class ViewRenderSpec extends Specs2TestContext {

  "The ViewRenderer" should {

    "render a record as HTML" in {
      withTestConfig {

        val configuration = OrganizationConfigurationHandler.getByOrgId("delving")

        val namespaces = Map("delving" -> "http://www.delving.eu/schemas/delving-1.0.xsd", "dc" -> "http://dublincore.org/schemas/xmls/qdc/dc.xsd", "icn" -> "http://www.icn.nl/schemas/ICN-V3.2.xsd")

        val renderer = new ViewRenderer("icn", ViewType.HTML, configuration)
        val view = renderer.renderRecordWithView("icn", ViewType.HTML, testHtmlViewDefinition, testRecord(), List(Role("administrator", Map("en" -> "blabla"))), namespaces, Lang("en"), Map.empty)

        val template = GenericTemplateLoader.load(Play2VirtualFile.fromFile(Play.getFile("test/view.html")))
        val args: java.util.Map[String, Object] = new java.util.HashMap[String, Object]()
        args.put("view", view.toViewTree)
        args.put("lang", "en")
        val rendered: String = template.render(args).replaceAll("""(?m)^\s+""", "")
        val expected: String =


          """<div class="root ">
            |<div class="row ">
            |<div class="column ">
            |<div  >
            |<h5>Description </h5>            <p>This is a test record</p>
            |</div>
            |</div>
            |<div class="column ">
            |<div  >
            |<h5>random</h5>
            |<p>A test hierarchical record, Wood</p>
            |<h5>Purchase Price  <span class="label">blabla</span></h5>            <p>5000</p>
            |<h5>metadata.icn.purchaseType </h5>            <p>auction</p>
            |<p><a href="http://foo.bar.com" data-type="" rel="nofollow">Blablabla</a></p>
            |</div>
            |</div>
            |<div class="column ">
            |<div  >
            |<h5>metadata.icn.placeName </h5>            <p>Paris</p>
            |<h5>metadata.icn.placeName </h5>            <p>Berlin</p>
            |<h5>metadata.icn.placeName </h5>            <p>Amsterdam</p>
            |</div>
            |</div>
            |</div>
            |</div>
            |""".stripMargin

        rendered must be equalTo(expected)
      }
    }

    "render an AFF record as HTML" in {
      withTestConfig {

        val configuration = OrganizationConfigurationHandler.getByOrgId("delving")

        val namespaces = Map("aff" -> "http://schemas.delving.eu/aff/aff_1.0.xsd")

        val affTestRecord = Source.fromFile(new File(Play.application.path, "test/resource/aff-example.xml")).getLines().mkString("\n")

        val renderer = new ViewRenderer("aff", ViewType.HTML, configuration)
        val view = renderer.renderRecord(affTestRecord, List.empty[Role], namespaces, Lang("en"))

        val template = GenericTemplateLoader.load(Play2VirtualFile.fromFile(Play.getFile("test/view.html")))
        val args: java.util.Map[String, Object] = new java.util.HashMap[String, Object]()
        args.put("view", view.toViewTree)
        args.put("lang", "en")
        val rendered: String = template.render(args).replaceAll("""(?m)^\s+""", "")

        1 must equalTo(1)
      }

    }

    "render a record as XML" in {
      withTestConfig {
        implicit val configuration = OrganizationConfigurationHandler.getByOrgId("delving")

        val namespaces = Map("delving" -> "http://www.delving.eu/schemas/delving-1.0.xsd", "dc" -> "http://dublincore.org/schemas/xmls/qdc/dc.xsd", "icn" -> "http://www.icn.nl/schemas/ICN-V3.2.xsd")

        val view = ViewRenderer.fromDefinition("aff", ViewType.API).get.renderRecordWithView("aff", ViewType.API, testXmlViewDefinition, testRecord(), List.empty, namespaces, Lang("en"), Map.empty)

        val xml = view.toXmlString

        val expected =
"""<?xml version="1.0" encoding="utf-8" ?>
 <record xmlns:dc="http://dublincore.org/schemas/xmls/qdc/dc.xsd" xmlns:delving="http://www.delving.eu/schemas/delving-1.0.xsd">
   <item id="42">
      <dc:title>A test hierarchical record</dc:title>
      <delving:description>This is a test record</delving:description>
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
    }


    "render a record as JSON" in {
      withTestConfig {
        val configuration = OrganizationConfigurationHandler.getByOrgId("delving")

        val namespaces = Map("delving" -> "http://www.delving.eu/schemas/delving-1.0.xsd", "dc" -> "http://dublincore.org/schemas/xmls/qdc/dc.xsd", "icn" -> "http://www.icn.nl/schemas/ICN-V3.2.xsd")
        val renderer = new ViewRenderer("aff", ViewType("api"), configuration)
        val view = renderer.renderRecordWithView("aff", ViewType("api"), testXmlViewDefinition, testRecord(), List.empty, namespaces, Lang("en"), Map.empty)
        val json = view.toJson

        val expected = """{"record":{"item":{"id":"42","dc_title":"A test hierarchical record","delving_description":"This is a test record","places":{"place":[{"country":"France","name":"Paris"},{"country":"Germany","name":"Berlin"},{"country":"Netherlands","name":"Amsterdam"}]}}}}"""

        json must equalTo (expected)
      }

    }

    val legacyNamespaces = Map(
      "dc" -> "http://purl.org/dc/elements/1.1/",
      "ese" -> "http://www.europeana.eu/schemas/ese/",
      "dcterms" -> "http://purl.org/dc/termes/",
      "europeana" -> "http://www.europeana.eu/schemas/ese/",
      "delving" -> "http://www.delving.eu/schemas/",
      "tib" -> "http://www.thuisinbrabant.nl/namespace"
    )

    "render a legacy record as JSON" in {
      withTestConfig {
        val configuration = OrganizationConfigurationHandler.getByOrgId("delving")

        val testRecord = legacyRecord()
        val renderer = new ViewRenderer("legacy", ViewType("api"), configuration)
        val view = renderer.renderRecord(testRecord, List.empty, legacyNamespaces, Lang("en"))

        val json = view.toJson

        val expected = """{"result":{"layout":{"fields":{"field":[{"name":"dc_creator","i18n":"Creator"},{"name":"dc_date","i18n":"Date"},{"name":"dc_format","i18n":"Format"},{"name":"dc_publisher","i18n":"Publisher"},{"name":"dc_title","i18n":"Title"},{"name":"dcterms_hasVersion","i18n":"Has version"},{"name":"delving_allSchemas","i18n":"metadata.delving.allSchemas"},{"name":"delving_currentFormat","i18n":"metadata.delving.currentFormat"},{"name":"delving_hasDigitalObject","i18n":"Record has a digital object"},{"name":"delving_hubId","i18n":"metadata.delving.hubId"},{"name":"delving_landingPage","i18n":"metadata.delving.landingPage"},{"name":"delving_orgId","i18n":"metadata.delving.orgId"},{"name":"delving_pmhId","i18n":"metadata.delving.pmhId"},{"name":"delving_publicSchemas","i18n":"metadata.delving.publicSchemas"},{"name":"delving_recordType","i18n":"Record type"},{"name":"delving_spec","i18n":"metadata.delving.spec"},{"name":"delving_thumbnail","i18n":"metadata.delving.thumbnail"},{"name":"delving_visibility","i18n":"metadata.delving.visibility"},{"name":"delving_year","i18n":"metadata.delving.year"},{"name":"europeana_collectionName","i18n":"Collection Name"},{"name":"europeana_collectionTitle","i18n":"Collection Title"},{"name":"europeana_country","i18n":"Country"},{"name":"europeana_dataProvider","i18n":"Data Provider"},{"name":"europeana_isShownAt","i18n":"Remote Landing Page"},{"name":"europeana_isShownBy","i18n":"Remote url to digital object"},{"name":"europeana_language","i18n":"Language"},{"name":"europeana_provider","i18n":"Provider"},{"name":"europeana_rights","i18n":"Rights"},{"name":"europeana_type","i18n":"Type"},{"name":"europeana_uri","i18n":"Europeana Url"},{"name":"tib_citName","i18n":"Cit collection name"},{"name":"tib_citOldId","i18n":"Cit record identifier"},{"name":"tib_collection","i18n":"Collection"},{"name":"tib_objectSoort","i18n":"Object type"},{"name":"tib_pageEnd","i18n":"End page"},{"name":"tib_pageStart","i18n":"Start page"},{"name":"tib_thumbLarge","i18n":"Large thumbnail"},{"name":"tib_thumbSmall","i18n":"Small thumbnail"}]}},"item":{"fields":{"dc_creator":["C."],"dc_date":["1965"],"dc_format":["application/pdf"],"dc_publisher":["De Brabantse Leeuw"],"dc_title":["GESLACHT RULO"],"dcterms_hasVersion":["JAARGANG XIV 1 9 6 5"],"delving_allSchemas":["raw","tib"],"delving_currentFormat":["tib"],"delving_hasDigitalObject":["true"],"delving_hubId":["thuisinbrabant_de-brabantse-leeuw_3967"],"delving_landingPage":["http://www.thuisinbrabant.nl/de-brabantse-leeuw/817"],"delving_orgId":["thuisinbrabant"],"delving_pmhId":["de-brabantse-leeuw_4ef0fdfb0cf21d42ad667346"],"delving_publicSchemas":["raw"],"delving_recordType":["mdr"],"delving_spec":["de-brabantse-leeuw"],"delving_thumbnail":["http://thuisinbrabant.delving.org/thumbnail/thuisinbrabant/de-brabantse-leeuw/brabants_leeuw_1965_1_87_96/500","http://thuisinbrabant.delving.org/thumbnail/thuisinbrabant/de-brabantse-leeuw/brabants_leeuw_1965_1_87_96/180"],"delving_visibility":["10"],"delving_year":["1965"],"europeana_collectionName":["de-brabantse-leeuw"],"europeana_collectionTitle":["De Brabantse Leeuw"],"europeana_country":["netherlands"],"europeana_dataProvider":["De Brabantse Leeuw"],"europeana_isShownAt":["http://www.thuisinbrabant.nl/de-brabantse-leeuw/817"],"europeana_isShownBy":["http://thuisinbrabant.delving.org/pdf/thuisinbrabant/de-brabantse-leeuw/brabants_leeuw_1965_1_87_96.pdf"],"europeana_language":["nl"],"europeana_provider":["Erfgoed Brabant"],"europeana_rights":["http://creativecommons.org/publicdomain/mark/1.0/"],"europeana_type":["TEXT"],"europeana_uri":["de-brabantse-leeuw/817"],"tib_citName":["ccBrabant_deBrabantseLeeuw"],"tib_citOldId":["ccBrabant_deBrabantseLeeuw_3967"],"tib_collection":["De Brabantse Leeuw"],"tib_objectSoort":["tijdschriftartikel","ontwerptekening"],"tib_pageEnd":["96"],"tib_pageStart":["87"],"tib_thumbLarge":["http://thuisinbrabant.delving.org/thumbnail/thuisinbrabant/de-brabantse-leeuw/brabants_leeuw_1965_1_87_96/500"],"tib_thumbSmall":["http://thuisinbrabant.delving.org/thumbnail/thuisinbrabant/de-brabantse-leeuw/brabants_leeuw_1965_1_87_96/180"]}},"relatedItems":""}}"""

        json must equalTo (expected)
      }

    }

    "render a legacy record as XML" in {
      withTestConfig {

        val configuration = OrganizationConfigurationHandler.getByOrgId("delving")

        val testRecord = legacyRecord()
        val renderer = new ViewRenderer("legacy", ViewType.API, configuration)
        val view = renderer.renderRecord(testRecord, List.empty, legacyNamespaces, Lang("en"))

        (view.toXml \ "layout" \ "fields" \ "field").filter(c => (c \ "name").text == "tib_objectSoort").size must equalTo(1)
      }

    }

  }








  private def testHtmlViewDefinition =
    <view name="full">
      <row>
        <column id="description">
          <container>
              <field path="/record/delving:summaryFields/delving:description" label="metadata.dc.description"/>
          </container>
        </column>
        <column id="fields">
          <container>
              <enumeration type="concatenated" separator=", " label="random" path="/record/delving:summaryFields/delving:title, /record/icn:data/icn:general/icn:material"/>
              <field path="/record/icn:data/icn:acquisition/icn:cost" label="metadata.icn.purchasePrice" role="administrator, own"/>
              <field path="/record/icn:data/icn:acquisition/@type" label="metadata.icn.purchaseType"/>
              <link urlExpr="/record/dc:data/dc:link" textExpr="/record/dc:data/dc:name" />
          </container>
        </column>
        <column id="complexFields">
          <container>
            <list path="/record/icn:places/icn:place">
                <field path="@name" label="metadata.icn.placeName"/>
            </list>
          </container>
         </column>
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
      """<?xml version="1.0" encoding="utf-8" ?>
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
    """<record xmlns:delving="http://www.delving.eu/schemas/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:europeana="http://www.europeana.eu/schemas/ese/" xmlns:icn="http://www.icn.nl/schemas/icn/" xmlns:tib="http://www.thuisinbrabant.nl/namespace">
      |  <dc:creator>C.</dc:creator>
      |  <dc:date>1965</dc:date>
      |  <dc:format>application/pdf</dc:format>
      |  <dc:publisher>De Brabantse Leeuw</dc:publisher>
      |  <dc:title>GESLACHT RULO</dc:title>
      |  <dcterms:hasVersion>JAARGANG XIV 1 9 6 5</dcterms:hasVersion>
      |  <delving:allSchemas>raw</delving:allSchemas>
      |  <delving:allSchemas>tib</delving:allSchemas>
      |  <delving:currentFormat>tib</delving:currentFormat>
      |  <delving:hasDigitalObject>true</delving:hasDigitalObject>
      |  <delving:hubId>thuisinbrabant_de-brabantse-leeuw_3967</delving:hubId>
      |  <delving:landingPage>http://www.thuisinbrabant.nl/de-brabantse-leeuw/817</delving:landingPage>
      |  <delving:orgId>thuisinbrabant</delving:orgId>
      |  <delving:pmhId>de-brabantse-leeuw_4ef0fdfb0cf21d42ad667346</delving:pmhId>
      |  <delving:publicSchemas>raw</delving:publicSchemas>
      |  <delving:recordType>mdr</delving:recordType>
      |  <delving:spec>de-brabantse-leeuw</delving:spec>
      |  <delving:thumbnail>http://thuisinbrabant.delving.org/thumbnail/thuisinbrabant/de-brabantse-leeuw/brabants_leeuw_1965_1_87_96/500</delving:thumbnail>
      |  <delving:thumbnail>http://thuisinbrabant.delving.org/thumbnail/thuisinbrabant/de-brabantse-leeuw/brabants_leeuw_1965_1_87_96/180</delving:thumbnail>
      |  <delving:visibility>10</delving:visibility>
      |  <delving:year>1965</delving:year>
      |  <europeana:collectionName>de-brabantse-leeuw</europeana:collectionName>
      |  <europeana:collectionTitle>De Brabantse Leeuw</europeana:collectionTitle>
      |  <europeana:country>netherlands</europeana:country>
      |  <europeana:dataProvider>De Brabantse Leeuw</europeana:dataProvider>
      |  <europeana:isShownAt>http://www.thuisinbrabant.nl/de-brabantse-leeuw/817</europeana:isShownAt>
      |  <europeana:isShownBy>http://thuisinbrabant.delving.org/pdf/thuisinbrabant/de-brabantse-leeuw/brabants_leeuw_1965_1_87_96.pdf</europeana:isShownBy>
      |  <europeana:language>nl</europeana:language>
      |  <europeana:provider>Erfgoed Brabant</europeana:provider>
      |  <europeana:rights>http://creativecommons.org/publicdomain/mark/1.0/</europeana:rights>
      |  <europeana:type>TEXT</europeana:type>
      |  <europeana:uri>de-brabantse-leeuw/817</europeana:uri>
      |  <tib:citName>ccBrabant_deBrabantseLeeuw</tib:citName>
      |  <tib:citOldId>ccBrabant_deBrabantseLeeuw_3967</tib:citOldId>
      |  <tib:collection>De Brabantse Leeuw</tib:collection>
      |  <tib:objectSoort>tijdschriftartikel</tib:objectSoort>
      |  <tib:objectSoort>ontwerptekening</tib:objectSoort>
      |  <tib:pageEnd>96</tib:pageEnd>
      |  <tib:pageStart>87</tib:pageStart>
      |  <tib:thumbLarge>http://thuisinbrabant.delving.org/thumbnail/thuisinbrabant/de-brabantse-leeuw/brabants_leeuw_1965_1_87_96/500</tib:thumbLarge>
      |  <tib:thumbSmall>http://thuisinbrabant.delving.org/thumbnail/thuisinbrabant/de-brabantse-leeuw/brabants_leeuw_1965_1_87_96/180</tib:thumbSmall>
      |  </record>""".stripMargin

}