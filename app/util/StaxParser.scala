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

