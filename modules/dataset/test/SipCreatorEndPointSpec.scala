import collection.mutable.{ Buffer, ListBuffer }
import controllers.dataset
import controllers.dataset.SipCreatorEndPoint
import core.HubModule
import java.io._
import java.util.zip.{ ZipInputStream, GZIPInputStream }
import org.apache.commons.io.IOUtils
import collection.JavaConverters._
import play.api.test._
import play.api.test.Helpers._
import models._
import play.api.mvc._
import play.api.libs.Files.TemporaryFile
import util.OrganizationConfigurationHandler
import xml.XML
import org.apache.commons.io.FileUtils

class SipCreatorEndPointSpec extends BootstrapAwareSpec {

  step {
    loadStandalone(SAMPLE_A, SAMPLE_B)
  }

  "SipCreatorEndPoint" should {

    def endPoint = new SipCreatorEndPoint()(HubModule)

    "list all DataSets" in {

      withTestConfig {
        val result = endPoint.listAll(Some("TEST"))(FakeRequest())
        status(result) must equalTo(OK)
        val stringResult: String = contentAsString(result)
        stringResult must contain("<spec>sample-a</spec>")
        stringResult must contain("<spec>sample-b</spec>")
      }

    }

    "unlock a DataSet" in {

      import com.mongodb.casbah.Imports._
      DataSet.dao(bootstrap.org).update(MongoDBObject("spec" -> bootstrap.spec), $set("lockedBy" -> "bob"))

      withTestConfig {
        val result = endPoint.unlock(
          bootstrap.org,
          bootstrap.spec,
          Some("TEST")
        )(FakeRequest())
        status(result) must equalTo(OK)
        DataSet.dao(bootstrap.org).findBySpecAndOrgId(bootstrap.spec, bootstrap.org).get.lockedBy must be(None)
      }

    }

    "accept a list of files" in {
      withTestConfig {
        val lines = bootstrap.fileNamesString()
        val result = endPoint.acceptFileList(
          bootstrap.org,
          bootstrap.spec,
          Some("TEST")
        )(
            FakeRequest(
              method = "POST",
              body = new AnyContentAsText(lines.stripMargin),
              uri = "",
              headers = FakeHeaders(Seq(CONTENT_TYPE -> Seq("text/plain")))
            ))
        status(result) must equalTo(OK)
        contentAsString(result) must equalTo(lines)
      }

      // TODO when we have a test DataSet, check this against the existing hashes
    }

    "accept a hints file" in {
      withTestConfig {
        val hintsFile = bootstrap.file("hints.txt")
        val result = endPoint.acceptFile(
          bootstrap.org,
          bootstrap.spec,
          hintsFile.getName,
          Some("TEST")
        )(
            FakeRequest(
              method = "POST",
              uri = "",
              headers = FakeHeaders(Seq(CONTENT_TYPE -> Seq("text/plain"))),
              body = TemporaryFile(hintsFile)
            )
          )
        status(result) must equalTo(OK)

        val stored = DataSet.dao(bootstrap.org).findBySpecAndOrgId(bootstrap.spec, bootstrap.org).get.hints

        val original = FileUtils.readFileToByteArray(hintsFile)

        stored must equalTo(original)
      }
    }

    "accept a mappings file" in {
      withTestConfig {
        val mappingFile = bootstrap.file("mapping_icn.xml")
        val result = endPoint.acceptFile(
          bootstrap.org,
          bootstrap.spec,
          mappingFile.getName,
          Some("TEST")
        )(
            FakeRequest(
              method = "POST",
              uri = "",
              headers = FakeHeaders(Seq(CONTENT_TYPE -> Seq("text/plain"))),
              body = TemporaryFile(mappingFile)
            )
          )
        status(result) must equalTo(OK)

        val mapping: String = DataSet.dao(bootstrap.org).findBySpecAndOrgId(bootstrap.spec, bootstrap.org)
          .get.mappings("icn").recordMapping.getOrElse(throw new RuntimeException)
        val original = FileUtils.readFileToString(mappingFile)

        mapping.trim must equalTo(original.trim)
      }
    }

    "accept a int file" in {
      withTestConfig {
        val intFile = bootstrap.file("validation_icn.int")

        val result = endPoint.acceptFile(
          bootstrap.org,
          bootstrap.spec,
          intFile.getName,
          Some("TEST")
        )(
            FakeRequest(
              method = "POST",
              uri = "",
              headers = FakeHeaders(Seq(CONTENT_TYPE -> Seq("application/octet-stream"))),
              body = TemporaryFile(intFile)
            )
          )
        status(result) must equalTo(OK)

        val original = readIntFile(intFile)

        val uploaded = DataSet.dao(bootstrap.org).findBySpecAndOrgId(bootstrap.spec, bootstrap.org).get.invalidRecords

        val invalidRecords = readInvalidIndexes(uploaded)

        original must equalTo(invalidRecords("icn"))
      }
    }

    "accept a links file" in {
      withTestConfig {
        val linksFile = bootstrap.file("links_abm.csv.gz")

        val result = endPoint.acceptFile(
          bootstrap.org,
          bootstrap.spec,
          linksFile.getName,
          Some("TEST")
        )(
            FakeRequest(
              method = "POST",
              uri = "",
              headers = FakeHeaders(Seq(CONTENT_TYPE -> Seq("application/octet-stream"))),
              body = TemporaryFile(linksFile)
            )
          )
        status(result) must equalTo(OK)

        val original = linksFile

        val uploaded = DataSet.dao(bootstrap.org).getLinksFile(bootstrap.spec, bootstrap.org, "abm")

        uploaded must beSome

        IOUtils.contentEquals(new FileInputStream(original), uploaded.get.inputStream) must beTrue
      }
    }

    "accept a source file" in {
      withTestConfig {
        val sourceFile = bootstrap.file("source.xml.gz")

        val result = endPoint.acceptFile(
          bootstrap.org,
          bootstrap.spec,
          sourceFile.getName,
          Some("TEST")
        )(
            FakeRequest(
              method = "POST",
              uri = "",
              headers = FakeHeaders(Seq(CONTENT_TYPE -> Seq("application/x-gzip"))),
              body = TemporaryFile(sourceFile)
            )
          )
        status(result) must equalTo(OK)

        val dataSet = DataSet.dao(bootstrap.org).findBySpecAndOrgId(bootstrap.spec, bootstrap.org).get

        implicit val configuration = OrganizationConfigurationHandler.getByOrgId("delving")
        while (DataSet.dao.getState(dataSet.orgId, dataSet.spec) == DataSetState.PARSING) Thread.sleep(100)
        // BaseX needs some time to flush the data to disk
        Thread.sleep(1000)

        DataSet.dao(bootstrap.org).getSourceRecordCount(dataSet) must equalTo(8)
      }
    }

    "have marked all file hashes and not accept them again" in {
      withTestConfig {
        bootstrap.init()
        val lines = bootstrap.fileNamesString()
        val result = endPoint.acceptFileList(
          bootstrap.org,
          bootstrap.spec,
          Some("TEST")
        )(
            FakeRequest(
              method = "POST",
              body = new AnyContentAsText(lines),
              uri = "",
              headers = FakeHeaders(Seq(CONTENT_TYPE -> Seq("text/plain")))
            ))
        status(result) must equalTo(OK)
        contentAsString(result) must equalTo("")
      }
    }

    "update an int file" in {
      withTestConfig {
        bootstrap.init()
        val intFile = bootstrap.file("validation_icn.int")
        val result = endPoint.acceptFile(
          bootstrap.org,
          bootstrap.spec,
          intFile.getName,
          Some("TEST")
        )(
            FakeRequest(
              method = "POST",
              uri = "",
              headers = FakeHeaders(Seq(CONTENT_TYPE -> Seq("text/plain"))), // ????
              body = TemporaryFile(intFile)
            )
          )
        status(result) must equalTo(OK)

        val original = readIntFile(intFile)

        val uploaded = DataSet.dao(bootstrap.org).findBySpecAndOrgId(bootstrap.spec, bootstrap.org).get.invalidRecords

        val invalidRecords = readInvalidIndexes(uploaded)

        original must equalTo(invalidRecords("icn"))
      }
    }

    "download a source file" in {

      case class ZipEntry(name: String)

      withTestConfig {

        implicit val configuration = OrganizationConfigurationHandler.getByOrgId(bootstrap.org)

        bootstrap.init()

        val dataSet = DataSet.dao(bootstrap.org).findBySpecAndOrgId(bootstrap.spec, bootstrap.org).get
        val sourceFile = bootstrap.file("source.xml.gz")

        // first, ingest all sorts of things
        val gis = new GZIPInputStream(new FileInputStream(sourceFile))
        dataset.SipCreatorEndPointHelper.loadSourceData(dataSet, gis)
        gis.close()

        val result = asyncToResult(endPoint.fetchSIP(
          bootstrap.org,
          bootstrap.spec,
          Some("TEST")
        )(
            FakeRequest()
          ))
        status(result) must equalTo(OK)

        // check locking
        val lockedDataSet = DataSet.dao(bootstrap.org).findBySpecAndOrgId(bootstrap.spec, bootstrap.org).get
        lockedDataSet.lockedBy must equalTo(Some("bob")) // TEST user

        // check the resulting set, indirectly
        val tempFile = File.createTempFile("sip-creator-endpoint-spec", "zip")
        val outputStream = new FileOutputStream(tempFile)
        endPoint.writeSipStream(lockedDataSet, outputStream)
        Thread.sleep(1000)

        var downloadedSource = ""
        val zis = new ZipInputStream(new FileInputStream(tempFile))
        var entry = zis.getNextEntry
        val downloadedEntries = Buffer[ZipEntry]()
        while (entry != null) {
          downloadedEntries += ZipEntry(entry.getName)
          if (entry.getName == "source.xml") {
            val source = Stream.continually(zis.read()).takeWhile(-1 !=).map(_.toByte).toArray
            downloadedSource = new String(source, "UTF-8")
          }
          entry = zis.getNextEntry
        }
        zis.close()

        XML.loadString(downloadedSource).size must equalTo(1)
        downloadedEntries.size must equalTo(5)

        val fis2 = new FileInputStream(sourceFile)
        val gis2 = new GZIPInputStream(fis2)
        val originalSource = IOUtils.readLines(gis2).asScala.mkString("\n")
        gis2.close()
        fis2.close()

        val os1 = new FileOutputStream(new File("/tmp/1.txt"))
        IOUtils.write(downloadedSource, os1)
        val os2 = new FileOutputStream(new File("/tmp/2.txt"))
        IOUtils.write(originalSource, os2)
        os1.close()
        os2.close()

        downloadedSource must equalTo(originalSource)

      }
    }
  }

  step {
    cleanup()
  }

  def readIntFile(file: File) = {
    val originalStream = new DataInputStream(new FileInputStream(file))
    val length = originalStream.readInt()
    val b = new ListBuffer[Int]()
    var counter = 0
    if (length == 0) {
      List()
    } else {
      while (counter < length) {
        counter += 1
        b += originalStream.readInt()
      }
      b.toList
    }
  }

  def readInvalidIndexes(uploaded: Map[String, List[Int]]) = {
    uploaded.map(valid => {
      val key = valid._1.toString
      val value: List[Int] = valid._2.asInstanceOf[com.mongodb.BasicDBList].asScala.map(index => index match {
        case int if int.isInstanceOf[Int] => int.asInstanceOf[Int]
        case double if double.isInstanceOf[java.lang.Double] => double.asInstanceOf[java.lang.Double].intValue()
      }).toList
      (key, value)
    }).toMap[String, List[Int]]
  }

}