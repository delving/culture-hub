import core.SystemField
import java.io.File
import models.MetadataCache
import org.apache.solr.client.solrj.SolrQuery
import org.scalatest.{ Ignore, FlatSpec }
import org.scalatest.matchers.ShouldMatchers
import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.mvc.AnyContentAsJson
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest }
import plugins.SimpleDocumentUploadPlugin
import test.TestContext
import util.OrganizationConfigurationHandler
import xml.XML
import services.search.SolrQueryService

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
@Ignore class SimpleDocumentUploadSpec extends FlatSpec with ShouldMatchers with TestContext {

  "The SimpleDocumentUpload" should "submit and store a document" in {

    withTestData() {

      implicit val configuration = OrganizationConfigurationHandler.getByOrgId("delving")

      val result = controllers.organizations.SimpleDocumentUpload.submit("delving")(fakeRequest)
      status(result) should equal(OK)

      val maybeDoc = MetadataCache.get("delving", "uploadDocuments", "uploadDocument").findOne("delving_uploadDocuments_503e203903643da47461306e")
      maybeDoc should not equal (None)
      val doc = maybeDoc.get

      doc.getSystemFieldValues(SystemField.TITLE).headOption should equal(Some("Sample title"))
      doc.index should equal(0)
      doc.schemaVersions.get("tib") should equal(Some("1.0.1"))
      doc.xml("tib") should equal("""<tib:record xmlns:europeana="http://www.europeana.eu/schemas/ese/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:tib="http://www.tib.nl/schemas/tib/" xmlns:delving="http://schemas.delving.eu/"><dc:title>Sample title</dc:title><dc:subject>Random subject</dc:subject><delving:title>Sample title</delving:title></tib:record>""")
    }

  }

  "The SimpleDocumentUpload" should "submit and index a document" in {
    withTestData() {

      implicit val configuration = OrganizationConfigurationHandler.getByOrgId("delving")

      val result = controllers.organizations.SimpleDocumentUpload.submit("delving")(fakeRequest)
      status(result) should equal(OK)

      val queryById = SolrQueryService.getSolrResponseFromServer(new SolrQuery("delving_orgId:delving id:delving_uploadDocuments_503e203903643da47461306e"))
      queryById.getResults.size() should equal(1)
    }
  }

  "The SimpleDocumentUpload" should "propertly integrate uploaded files into a document" in {

    withTestData() {

      import play.api.Play.current

      implicit val configuration = OrganizationConfigurationHandler.getByOrgId("delving")

      indexingServiceLocator.byDomain(configuration).deleteByQuery("*:*")

      val pdf = new File(current.path, "/modules/simple-document-upload/conf/sample.pdf")
      val png = new File(current.path, "public/images/dummy-object.png")
      val uid = "123456"

      val (f, thumbnailUrl) = controllers.dos.FileUpload.storeFile(pdf, "sample.pdf", "application/pdf", uid).get
      val (f1, thumbnailUrl1) = controllers.dos.FileUpload.storeFile(png, "delving-team.jpg", "image/jpg", uid).get

      val simulatedAttachment = controllers.organizations.SimpleDocumentUpload.upload("delving", uid, "delving_uploadDocuments_503e203903643da47461306e")(
        FakeRequest().withSession(
          ("userName" -> "bob")
        ))

      status(simulatedAttachment) should equal(OK)

      val result = controllers.organizations.SimpleDocumentUpload.submit("delving")(fakeRequest)
      status(result) should equal(OK)

      val maybeDoc = MetadataCache.get("delving", "uploadDocuments", SimpleDocumentUploadPlugin.ITEM_TYPE).findOne("delving_uploadDocuments_503e203903643da47461306e")
      maybeDoc should not equal (None)
      val doc = maybeDoc.get

      doc.xml.get("tib") should not equal (None)
      val content = doc.xml("tib")

      val parsed = XML.loadString(content)

      (parsed \ "title").find(_.prefix == "delving").map(_.text) should equal(Some("Sample title"))
      (parsed \ "title").find(_.prefix == "dc").map(_.text) should equal(Some("Sample title"))
      (parsed \ "subject").text should equal("Random subject")
      (parsed \ "imageUrl").text should equal("http:///image/" + f1.id.toString)
      (parsed \ "thumbnail").map(_.text + "/80") should equal(Seq("http://" + thumbnailUrl1, "http://" + thumbnailUrl))
      (parsed \ "fullTextObjectUrl").text should equal("http:///file/" + f.id.toString)

      val queryById = SolrQueryService.getSolrResponseFromServer(new SolrQuery("delving_orgId:delving id:delving_uploadDocuments_503e203903643da47461306e"))
      queryById.getResults.size() should equal(1)

      val solrDocument = queryById.getResults.get(0)

      solrDocument.getFirstValue("delving_fullTextObjectUrl_link") should equal("http:///file/" + f.id.toString)
      solrDocument.getFirstValue("delving_imageUrl") should equal("http:///image/" + f1.id.toString)
      solrDocument.getFirstValue("delving_thumbnail") should equal("http:///thumbnail/" + f1.id.toString)
      solrDocument.getFirstValue("delving_hasDigitalObject") should equal(true)

    }

  }

  // re-enable this if this plugin gets ever used seriously
  //
  //  "The SimpleDocumentUpload" should "index a PDF as full text with Tika" in {
  //    withTestData() {
  //
  //      import play.api.Play.current
  //
  //      implicit val configuration = OrganizationConfigurationHandler.getByOrgId("delving")
  //
  //      val pdf = new File(current.path, "../modules/simple-document-upload/conf/sample.pdf")
  //      val uid = "123456"
  //
  //      val (f, thumbnailUrl) = controllers.dos.FileUpload.storeFile(pdf, "sample.pdf", "application/pdf", uid).get
  //
  //      val simulatedAttachment = controllers.organizations.SimpleDocumentUpload.upload("delving", uid, "delving_uploadDocuments_503e203903643da47461306e")(
  //        FakeRequest().withSession(
  //          ("userName" -> "bob")
  //        ))
  //
  //      status(simulatedAttachment) should equal(OK)
  //
  //      val result = controllers.organizations.SimpleDocumentUpload.submit("delving")(fakeRequest)
  //      status(result) should equal(OK)
  //
  //      val queryFullText = SolrQueryService.getSolrResponseFromServer(new SolrQuery("Anticonstitutionellement"))
  //      // FIXME this does not work yet because due to how the Tika indexing is implemented, the remote fetching in the test scope without real URL does not work
  //      //      queryFullText.getResults.size() should equal(1)
  //    }
  //  }

  def fakeRequest = FakeRequest(
    method = "POST",
    uri = "http://delving.localhost:9000",
    headers = FakeHeaders(Seq(CONTENT_TYPE -> Seq("application/json"))),
    body = AnyContentAsJson(
      JsObject(
        Seq(
          "id" -> JsString("delving_uploadDocuments_503e203903643da47461306e"),
          "fields" -> JsArray(
            Seq(
              JsObject(Seq("key" -> JsString("dc:title"), "fieldType" -> JsString("text"), "label" -> JsString("Title"), "value" -> JsString("Sample title"))),
              JsObject(Seq("key" -> JsString("dc:subject"), "fieldType" -> JsString("text"), "label" -> JsString("metadata.dc.subject"), "value" -> JsString("Random subject")))
            )
          ),
          "files" -> JsArray(Seq.empty)
        )
      )
    )
  ).withSession(
      ("userName" -> "bob")
    )

}