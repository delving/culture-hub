package eu.delving.core

import _root_.java.io.PrintWriter
import _root_.java.net.URL
import _root_.java.util.Date
import xml.{NodeSeq, Elem, XML}
import scala.actors._
import Actor._

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since Apr 8, 2010 9:07:46 AM
 */

object Harvester {
  def main(args: Array[String]) = {
    val exitMessage = harvestWithActors(args toList)
    println(exitMessage)
  }

  def harvestWithActors(baseUrls: List[String]): String = {
    val caller = self

    baseUrls foreach {
      baseUrl =>
        {
          val infoList = baseUrl.split(":::").toList
          infoList match {
            case emptyList: List[_] if emptyList.exists(_.isEmpty) => actor {caller ! processError(emptyList.toString)}
            case List(url: String) if infoList.forall(!_.isEmpty) => actor {caller ! writeRecordsToFile(url, "harvestFile-" + (new Date).toString)}
            case List(url: String, fileName: String) => actor {caller ! writeRecordsToFile(url, fileName)}
            case List(url: String, fileName: String, metadataPrefix: String) => actor {caller ! writeRecordsToFile(url, fileName, metadataPrefix)}
            case List(url: String, fileName: String, metadataPrefix: String, setSpec: String) =>
              actor {caller ! writeRecordsToFile(url, fileName, metadataPrefix, setSpec)}
            case _ => println("nothing found")
          }
        }
    }

    for (i <- 1 to baseUrls.size) {
      receive {
        case processLog(pages, records, baseUrl) =>
          println(format("processed records %d in %d pages for %s as %d response", records, pages, baseUrl, i))
        case processError(message) => println("Unable to process:" + message)
      }
    }
    "finished harvesting"
  }

  def writeRecordsToFile(baseUrl: String, fileName: String, metadataPrefix: String = "oai_dc", setSpec: String = ""): processLog = {
    require(!baseUrl.isEmpty && !fileName.isEmpty && !metadataPrefix.isEmpty)

    val hasSet: String = if (!setSpec.isEmpty) "&set=" + setSpec else setSpec

    def getRecords(baseUrl: String, writer: PrintWriter, resumptionToken: String = "": String,
                   pagesRetrieved: Int = 0, recordsRetrieved: Int = 0): (Int, Int) = {
      val elem: Elem =
      if (resumptionToken.isEmpty)
        XML.load(new URL(baseUrl + "?verb=ListRecords&accessKey=CW-E02A6689E8B183A5915A&metadataPrefix=" + metadataPrefix + hasSet))
      else
        XML.load(new URL(baseUrl + "?verb=ListRecords&accessKey=CW-E02A6689E8B183A5915A&resumptionToken=" + resumptionToken))

      val token: NodeSeq = elem \\ "resumptionToken"
      val completeListSize = (elem \\ "@completeListSize").text
      val cursor = (elem \\ "@cursor").text
      val records = elem \\ "record"
      records.foreach(f => writer write (f.toString))
      val totalRecordsRetrieved = records.size + recordsRetrieved
      if (totalRecordsRetrieved % 1000 == 0)
        println(format("[%s of %s] => %s : (set %s with prefix %s to file %s) : (%d records in %d requests on %s)",
          cursor, completeListSize, baseUrl, setSpec, metadataPrefix, fileName, totalRecordsRetrieved, pagesRetrieved, (new Date().toString)))
      if (token.text.isEmpty) // use for testing || pagesRetrieved >= 1
        (pagesRetrieved + 1, totalRecordsRetrieved)
      else
        getRecords(baseUrl, writer, token.text, pagesRetrieved + 1, totalRecordsRetrieved)
    }

    println("starting for: " + baseUrl)
    val writer: PrintWriter = new PrintWriter("/tmp/" + fileName + ".xml", "utf-8")
    try {
      writer write "<metadata>\n"
      val (pages, records) = getRecords(baseUrl, writer)
      writer write "\n</metadata>";
      writer.close
      processLog(pages, records, baseUrl)
    }
    finally {
      writer.close
    }
  }

  case class processLog(pages: Int, records: Int, baseUrl: String)
  case class processError(message: String)
}