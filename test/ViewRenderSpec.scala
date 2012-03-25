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
import groovy.util.{XmlParser, Node}
import models.GrantType
import org.specs2.mutable._
import play.api.Play
import play.api.Play.current
import play.api.test._
import play.api.test.Helpers._
import play.templates.GenericTemplateLoader

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class ViewRenderSpec extends Specification {

  "The ViewRenderer" should {

    "render a record" in {
      running(FakeApplication()) {

        val view = core.rendering.ViewRenderer.renderView("icn", testViewDefinition, "full", testRecord(), List(GrantType("administrator", "blabla", "icn")))
        val template = GenericTemplateLoader.load(Play2VirtualFile.fromFile(Play.getFile("test/view.html")))
        val args: java.util.Map[String, Object] = new java.util.HashMap[String, Object]()
        args.put("view", view)
        args.put("lang", "en")
        val rendered = template.render(args)

        println(rendered)

        1 should be equalTo (1)
      }
    }

  }

  private def testViewDefinition =
    <view name="full">
      <row>
        <column id="description">
            <field path="delving:summaryFields/delving:description" label="metadata.dc.description"/>
        </column>
        <column id="fields">
            <list type="concatenated" separator=", " label="random" path="delving:summaryFields/delving:title, icn:data/icn:general/icn:material"/>
            <field path="icn:data/icn:acquisition/icn:cost" label="metadata.icn.purchasePrice" role="administrator, own"/>
        </column>
      </row>
    </view>


  private def testRecord(): Node = {

    // test record, hierarchical
    val testRecord =
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
          <icn:acquisition>
            <icn:cost>5000</icn:cost>
          </icn:acquisition>
        </icn:data>
      </record>

    new XmlParser().parseText(testRecord.toString())
  }

}