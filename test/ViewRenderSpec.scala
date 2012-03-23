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

import core.rendering.RenderNode
import eu.delving.templates.Play2VirtualFile
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import models.GrantType
import org.specs2.mutable._
import org.w3c.dom.Document
import play.api.Play
import play.api.Play.current
import play.api.test._
import play.api.test.Helpers._
import play.libs.XPath
import play.templates.GenericTemplateLoader

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class ViewRenderSpec extends Specification {

  "The ViewRenderer" should {

    "render a record" in {
      running(FakeApplication()) {

        val namespaces = Map("delving" -> "http://www.delving.eu/schemas/delving-1.0.xsd", "dc" -> "http://dublincore.org/schemas/xmls/qdc/dc.xsd", "icn" -> "http://www.icn.nl/schemas/ICN-V3.2.xsd")

        val view = core.rendering.ViewRenderer.renderView("icn", testViewDefinition, "full", testRecord(), List(GrantType("administrator", "blabla", "icn")), namespaces)

        RenderNode.visit(view)

        println()
        println()

        val template = GenericTemplateLoader.load(Play2VirtualFile.fromFile(Play.getFile("test/view.html")))
        val args: java.util.Map[String, Object] = new java.util.HashMap[String, Object]()
        args.put("view", view)
        args.put("lang", "en")
        val rendered: String = template.render(args)
        val expected: String =
"""<div class="row">
<div class="column" id="description">
<div class="field">metadata.dc.description: This is a test record</div>
</div>
<div class="column" id="fields">
    <div>A test hierarchical record, Wood</div>
<div class="field">metadata.icn.purchasePrice: 5000</div>
<div class="field">metadata.icn.purchaseType: auction</div>
</div>
<div class="column" id="complexFields">
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

  }

  private def testViewDefinition =
    <view name="full">
      <row>
        <column id="description">
            <field path="/record/delving:summaryFields/delving:description" label="metadata.dc.description"/>
        </column>
        <column id="fields">
            <enumeration type="concatenated" separator=", " label="random" path="/record/delving:summaryFields/delving:title, /record/icn:data/icn:general/icn:material"/>
            <field path="/record/icn:data/icn:acquisition/icn:cost" label="metadata.icn.purchasePrice" role="administrator, own"/>
            <field path="/record/icn:data/icn:acquisition/@type" label="metadata.icn.purchaseType"/>
        </column>
        <column id="complexFields">
             <list path="/record/icn:places/icn:place">
                 <field path="@name" label="metadata.icn.placeName"/>
             </list>
         </column>
      </row>
    </view>


  private def testRecord(): Document = {

    // test record, hierarchical
      val doc = """<?xml version="1.0" encoding="iso-8859-1" ?>
      <record xmlns:delving="http://www.delving.eu/schemas/delving-1.0.xsd" xmlns:dc="http://dublincore.org/schemas/xmls/qdc/dc.xsd" xmlns:icn="http://www.icn.nl/schemas/ICN-V3.2.xsd">
        <delving:summaryFields>
          <delving:title>A test hierarchical record</delving:title>
          <delving:description>This is a test record</delving:description>
          <delving:creator>John Lennon</delving:creator>
          <delving:owner>Museum of Music</delving:owner>
        </delving:summaryFields>
        <dc:data>
          <dc:type>picture</dc:type>
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
          <icn:place name="Berlin" country="German" />
          <icn:place name="Amsterdam" country="Netherlands" />
        </icn:places>
      </record>"""

    val dbFactory = DocumentBuilderFactory.newInstance
    dbFactory.setNamespaceAware(true)
    val dBuilder = dbFactory.newDocumentBuilder
    val res = dBuilder.parse(new ByteArrayInputStream(doc.getBytes("utf-8")))
    res

  }

}