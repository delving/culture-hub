import collection.mutable.{Buffer, ListBuffer}
import controllers.SipCreatorEndPoint
import java.io._
import java.util.zip.{ZipInputStream, GZIPInputStream}
import org.apache.commons.io.IOUtils
import collection.JavaConverters._
import play.api.test._
import play.api.test.Helpers._
import models._
import play.api.mvc._
import play.api.libs.Files.TemporaryFile
import plugins.BootstrapSource
import util.DomainConfigurationHandler
import xml.XML
import org.apache.commons.io.FileUtils

class SipCreatorEndPointSpec extends Specs2TestContext {

    step {
        loadStandalone()
    }

    val boot = BootstrapSource.sources.head

    "SipCreatorEndPoint" should {

        "list all DataSets" in {

            withTestConfig {
                val result = controllers.SipCreatorEndPoint.listAll(Some("TEST"))(FakeRequest())
                status(result) must equalTo(OK)
                val stringResult: String = contentAsString(result)
                stringResult must contain("<spec>sample-a</spec>")
                stringResult must contain("<spec>sample-b</spec>")
            }

        }

        "unlock a DataSet" in {

            import com.mongodb.casbah.Imports._
            DataSet.dao(boot.org).update(MongoDBObject("spec" -> boot.spec), $set("lockedBy" -> "bob"))

            withTestConfig {
                val result = controllers.SipCreatorEndPoint.unlock(
                    boot.org,
                    boot.spec,
                    Some("TEST")
                )(FakeRequest())
                status(result) must equalTo(OK)
                DataSet.dao(boot.org).findBySpecAndOrgId(boot.spec, boot.org).get.lockedBy must be(None)
            }

        }

        "accept a list of files" in {
            withTestConfig {
                val lines = boot.fileNamesString()
                val result = controllers.SipCreatorEndPoint.acceptFileList(
                    boot.org,
                    boot.spec,
                    Some("TEST")
                )(
                    FakeRequest(
                        method = "POST",
                        body = new AnyContentAsText(lines.stripMargin),
                        uri = "",
                        headers = FakeHeaders(Map(CONTENT_TYPE -> Seq("text/plain")))
                    ))
                status(result) must equalTo(OK)
                contentAsString(result) must equalTo(lines)
            }

            // TODO when we have a test DataSet, check this against the existing hashes
        }

        "accept a hints file" in {
            withTestConfig {
                val hintsFile = boot.file("hints.txt")
                val result = controllers.SipCreatorEndPoint.acceptFile(
                    boot.org,
                    boot.spec,
                    hintsFile.getName,
                    Some("TEST")
                )(
                    FakeRequest(
                        method = "POST",
                        uri = "",
                        headers = FakeHeaders(Map(CONTENT_TYPE -> Seq("text/plain"))),
                        body = TemporaryFile(hintsFile)
                    )
                )
                status(result) must equalTo(OK)

                val stored = DataSet.dao(boot.org).findBySpecAndOrgId(boot.spec, boot.org).get.hints

                val original = FileUtils.readFileToByteArray(hintsFile)

                stored must equalTo(original)
            }
        }

        "accept a mappings file" in {
            withTestConfig {
                val mappingFile = boot.file("mapping_icn.xml")
                val result = controllers.SipCreatorEndPoint.acceptFile(
                    boot.org,
                    boot.spec,
                    mappingFile.getName,
                    Some("TEST")
                )(
                    FakeRequest(
                        method = "POST",
                        uri = "",
                        headers = FakeHeaders(Map(CONTENT_TYPE -> Seq("text/plain"))),
                        body = TemporaryFile(mappingFile)
                    )
                )
                status(result) must equalTo(OK)

                val mapping: String = DataSet.dao(boot.org).findBySpecAndOrgId(boot.spec, boot.org)
                                      .get.mappings("icn").recordMapping.getOrElse(throw new RuntimeException)
                val original = FileUtils.readFileToString(mappingFile)

                mapping.trim must equalTo(original.trim)
            }
        }

        "accept a int file" in {
            withTestConfig {
                val intFile = boot.file("validation_icn.int")

                val result = controllers.SipCreatorEndPoint.acceptFile(
                    boot.org,
                    boot.spec,
                    intFile.getName,
                    Some("TEST")
                )(
                    FakeRequest(
                        method = "POST",
                        uri = "",
                        headers = FakeHeaders(Map(CONTENT_TYPE -> Seq("application/octet-stream"))),
                        body = TemporaryFile(intFile)
                    )
                )
                status(result) must equalTo(OK)

                val original = readIntFile(intFile)

                val uploaded = DataSet.dao(boot.org).findBySpecAndOrgId(boot.spec, boot.org)
                               .get.invalidRecords

                val invalidRecords = readInvalidIndexes(uploaded)

                original must equalTo(invalidRecords("icn"))
            }
        }

        "accept a source file" in {
            withTestConfig {
                val sourceFile = boot.file("source.xml.gz")

                val result = controllers.SipCreatorEndPoint.acceptFile(
                    boot.org,
                    boot.spec,
                    sourceFile.getName,
                    Some("TEST")
                )(
                    FakeRequest(
                        method = "POST",
                        uri = "",
                        headers = FakeHeaders(Map(CONTENT_TYPE -> Seq("application/x-gzip"))),
                        body = TemporaryFile(sourceFile)
                    )
                )
                status(result) must equalTo(OK)

                val dataSet = DataSet.dao(boot.org).findBySpecAndOrgId(boot.spec, boot.org).get

                // now we wait since the parsing is asynchronous. We wait a long time since our CI server is rather slow.
                Thread.sleep(10000)

                DataSet.dao(boot.org).getSourceRecordCount(dataSet) must equalTo(8)
            }
        }

        "have marked all file hashes and not accept them again" in {
            withTestConfig {
                boot.init()
                val lines = boot.fileNamesString()
                val result = controllers.SipCreatorEndPoint.acceptFileList(
                    boot.org,
                    boot.spec,
                    Some("TEST")
                )(
                    FakeRequest(
                        method = "POST",
                        body = new AnyContentAsText(lines),
                        uri = "",
                        headers = FakeHeaders(Map(CONTENT_TYPE -> Seq("text/plain")))
                    ))
                status(result) must equalTo(OK)
                contentAsString(result) must equalTo("")
            }
        }

        "update an int file" in {
            withTestConfig {
                boot.init()
                val intFile = boot.file("validation_icn.int")
                val result = controllers.SipCreatorEndPoint.acceptFile(
                    boot.org,
                    boot.spec,
                    intFile.getName,
                    Some("TEST")
                )(
                    FakeRequest(
                    method = "POST",
                    uri = "",
                    headers = FakeHeaders(Map(CONTENT_TYPE -> Seq("text/plain"))), // ????
                    body = TemporaryFile(intFile)
                )
                )
                status(result) must equalTo(OK)

                val original = readIntFile(intFile)

                val uploaded = DataSet.dao(boot.org).findBySpecAndOrgId(boot.spec, boot.org)
                               .get.invalidRecords

                val invalidRecords = readInvalidIndexes(uploaded)

                original must equalTo(invalidRecords("icn"))
            }
        }

        "download a source file" in {

            case class ZipEntry(name: String)

            withTestConfig {

                implicit val configuration = DomainConfigurationHandler.getByOrgId(boot.org)

                boot.init()

                val dataSet = DataSet.dao(boot.org).findBySpecAndOrgId(boot.spec, boot.org).get
                val sourceFile = boot.file("source.xml.gz")

                // first, ingest all sorts of things
                val gis = new GZIPInputStream(new FileInputStream(sourceFile))
                SipCreatorEndPoint.loadSourceData(dataSet, gis)
                gis.close()

                val result = asyncToResult(controllers.SipCreatorEndPoint.fetchSIP(
                    boot.org,
                    boot.spec,
                    Some("TEST")
                )(
                    FakeRequest()
                ))
                status(result) must equalTo(OK)

                // check locking
                val lockedDataSet = DataSet.dao(boot.org).findBySpecAndOrgId(boot.spec, boot.org).get
                lockedDataSet.lockedBy must equalTo(Some("bob")) // TEST user

                // check the resulting set, indirectly
                val is = SipCreatorEndPoint.getSipStream(lockedDataSet)
                Thread.sleep(1000)

                var downloadedSource = ""
                val zis = new ZipInputStream(new FileInputStream(is))
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
                downloadedEntries.size must equalTo(4)

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
        }
        else {
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

