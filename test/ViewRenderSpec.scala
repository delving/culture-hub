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

import groovy.util.XmlParser
import models.GrantType
import org.junit.{Ignore, Test}
import org.scalatest.matchers.ShouldMatchers
import play.Play
import play.templates.TemplateLoader
import play.test.UnitTest
import play.vfs.VirtualFile
import util.TestDataGeneric
import groovy.util.Node

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class ViewRenderSpec extends UnitTest with ShouldMatchers with TestDataGeneric {

  @Ignore
  @Test
  def testRender() {
    val view = components.ViewRenderer.renderView(play.Play.getFile("conf/icn-record-definition.xml").getAbsoluteFile, "full", testData(), List(GrantType("administrator", "blabla", "icn")))
    val template = TemplateLoader.load(VirtualFile.open(Play.getFile("test/view.html")))
    val args: java.util.Map[String, Object] = new java.util.HashMap[String, Object]()
    args.put("view", view)
    val rendered = template.render(args)

    println(rendered)
  }
  
  private def testData(): Node = {

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