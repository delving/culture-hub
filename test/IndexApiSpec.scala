import com.mongodb.casbah.commons.MongoDBObject
import core.search.SolrQueryService
import models.IndexItem
import org.apache.solr.client.solrj.SolrQuery
import org.specs2.mutable.Specification
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest, FakeApplication}
import xml.XML
import scala.xml.Utility.trim

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class IndexApiSpec extends Specification with TestContext {

  "The Index Api" should {

    "process a request with 2 valid items" in {

      running(FakeApplication()) {

        val fakeRequest: FakeRequest[scala.xml.NodeSeq] = FakeRequest(
          method = "POST",
          uri = "",
          headers = FakeHeaders(Map(CONTENT_TYPE -> Seq("application/xml"))),
          body =  <indexRequest>
                    <indexItem itemId="123" itemType="book">
                      <field name="title" dataType="string">The Hitchhiker's Guide to the Galaxy</field>
                      <field name="author" fieldType="string" facet="true">Douglas Adams</field>
                    </indexItem>
                    <indexItem itemId="456" itemType="movie">
                      <field name="title" dataType="string">The Hitchhiker's Guide to the Galaxy</field>
                      <field name="director" fieldType="string" facet="true">Garth Jennings</field>
                    </indexItem>
                  </indexRequest>

        )

        val result = asyncToResult(controllers.api.Index.submit("delving")(fakeRequest))
        status(result) must equalTo(OK)

        val expected = <indexResponse>
                        <totalItemCount>2</totalItemCount>
                        <indexedItemCount>2</indexedItemCount>
                        <deletedItemCount>0</deletedItemCount>
                        <invalidItemCount>0</invalidItemCount>
                        <invalidItems />
                      </indexResponse>

        trim(XML.loadString(contentAsString(result))) must equalTo(trim(expected))

        val mongoCache = IndexItem.find(MongoDBObject()).toList

        mongoCache.size must equalTo(2)

        val queryByType = SolrQueryService.getSolrResponseFromServer(new SolrQuery("delving_orgId:delving delving_recordType:book"))
        val queryById = SolrQueryService.getSolrResponseFromServer(new SolrQuery("delving_orgId:delving id:123"))

        queryByType.getResults.size() must equalTo(1)
        queryById.getResults.size() must equalTo(1)
      }
    }

    "reject items without itemId" in {

      running(FakeApplication()) {

        val fakeRequest: FakeRequest[scala.xml.NodeSeq] = FakeRequest(
          method = "POST",
          uri = "",
          headers = FakeHeaders(Map(CONTENT_TYPE -> Seq("application/xml"))),
          body =  <indexRequest>
                    <indexItem itemId="123" itemType="book">
                      <field name="title" dataType="string">The Hitchhiker's Guide to the Galaxy</field>
                      <field name="author" fieldType="string" facet="true">Douglas Adams</field>
                    </indexItem>
                    <indexItem itemType="movie">
                      <field name="title" dataType="string">The Hitchhiker's Guide to the Galaxy</field>
                      <field name="director" fieldType="string" facet="true">Garth Jennings</field>
                    </indexItem>
                  </indexRequest>

        )

        val result = asyncToResult(controllers.api.Index.submit("delving")(fakeRequest))
        status(result) must equalTo(OK)

        val expected = <indexResponse>
                        <totalItemCount>2</totalItemCount>
                        <indexedItemCount>1</indexedItemCount>
                        <deletedItemCount>0</deletedItemCount>
                        <invalidItemCount>1</invalidItemCount>
                        <invalidItems>
                          <indexItem itemType="movie">
                            <field name="title" dataType="string">The Hitchhiker's Guide to the Galaxy</field>
                            <field name="director" fieldType="string" facet="true">Garth Jennings</field>
                          </indexItem>
                        </invalidItems>
                      </indexResponse>

        trim(XML.loadString(contentAsString(result))) must equalTo(trim(expected))

      }
    }
  }

  step(cleanup)

}
