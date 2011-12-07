/*
 * Copyright 2011 Delving B.V.
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

package util

import org.codehaus.stax2.XMLInputFactory2
import org.codehaus.stax2.XMLStreamReader2
import javax.xml.transform.Source
import javax.xml.transform.stream.StreamSource
import java.io.InputStream

trait StaxParser {

  val xmlif: XMLInputFactory2 = StaxFactory.newInstance()
  xmlif.configureForSpeed()

  def createReader(inputStream: InputStream): XMLStreamReader2 = {
    val source: Source = new StreamSource(inputStream, "UTF-8")
    xmlif.createXMLStreamReader(source).asInstanceOf[XMLStreamReader2]
  }
}

