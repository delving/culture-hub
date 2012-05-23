import com.mongodb.casbah.commons.MongoDBObject
import core.indexing.IndexingService
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

  val testItems = <indexRequest>
                      <indexItem itemId="123" itemType="book">
                        <field name="title" fieldType="string">The Hitchhiker's Guide to the Galaxy</field>
                        <field name="author" fieldType="string" facet="true">Douglas Adams</field>
                        <systemField name="thumbnail">http://upload.wikimedia.org/wikipedia/en/b/bd/H2G2_UK_front_cover.jpg</systemField>
                      </indexItem>
                      <indexItem itemId="456" itemType="movie">
                        <field name="title" fieldType="string">The Hitchhiker's Guide to the Galaxy</field>
                        <field name="director" fieldType="string" facet="true">Garth Jennings</field>
                        <systemField name="thumbnail">http://upload.wikimedia.org/wikipedia/en/7/7a/Hitchhikers_guide_to_the_galaxy.jpg</systemField>
                      </indexItem>
                    </indexRequest>

  "The Index Api" should {

    "process a request with 2 valid items" in {

      running(FakeApplication()) {

        val fakeRequest: FakeRequest[scala.xml.NodeSeq] = FakeRequest(
          method = "POST",
          uri = "",
          headers = FakeHeaders(Map(CONTENT_TYPE -> Seq("application/xml"))),
          body =  testItems
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
        val queryById = SolrQueryService.getSolrResponseFromServer(new SolrQuery("delving_orgId:delving id:delving_movie_456"))

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
                      <field name="title" fieldType="string">The Hitchhiker's Guide to the Galaxy</field>
                      <field name="author" fieldType="string" facet="true">Douglas Adams</field>
                    </indexItem>
                    <indexItem itemType="movie">
                      <field name="title" fieldType="string">The Hitchhiker's Guide to the Galaxy</field>
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
                          <invalidItem>
                            <error>Item misses required attributes 'itemId' or 'itemType'</error>
                            <item>
                              <indexItem itemType="movie">
                                <field name="title" fieldType="string">The Hitchhiker's Guide to the Galaxy</field>
                                <field name="director" fieldType="string" facet="true">Garth Jennings</field>
                              </indexItem>
                            </item>
                          </invalidItem>
                        </invalidItems>
                      </indexResponse>

        trim(XML.loadString(contentAsString(result))) must equalTo(trim(expected))

      }
    }

    "delete items" in {

        running(FakeApplication()) {

          val fakeRequest: FakeRequest[scala.xml.NodeSeq] = FakeRequest(
            method = "POST",
            uri = "",
            headers = FakeHeaders(Map(CONTENT_TYPE -> Seq("application/xml"))),
            body = testItems

          )
          val result = asyncToResult(controllers.api.Index.submit("delving")(fakeRequest))
          status(result) must equalTo(OK)

          val fakeDeleteRequest: FakeRequest[scala.xml.NodeSeq] = FakeRequest(
            method = "POST",
            uri = "",
            headers = FakeHeaders(Map(CONTENT_TYPE -> Seq("application/xml"))),
            body = <indexRequest>
                     <indexItem itemId="123" itemType="book" delete="true" />
                   </indexRequest>)

          val deleteResult = asyncToResult(controllers.api.Index.submit("delving")(fakeDeleteRequest))
          status(deleteResult) must equalTo(OK)


          val expected = <indexResponse>
                          <totalItemCount>1</totalItemCount>
                          <indexedItemCount>0</indexedItemCount>
                          <deletedItemCount>1</deletedItemCount>
                          <invalidItemCount>0</invalidItemCount>
                          <invalidItems />
                        </indexResponse>

          trim(XML.loadString(contentAsString(deleteResult))) must equalTo(trim(expected))

        }
      }
  }

  "process a request with thumbnail systemFields" in {

    running(FakeApplication()) {

      val fakeRequest: FakeRequest[scala.xml.NodeSeq] = FakeRequest(
        method = "POST",
        uri = "",
        headers = FakeHeaders(Map(CONTENT_TYPE -> Seq("application/xml"))),
        body =  <indexRequest>
                  <indexItem itemId="123" itemType="foo">
                    <systemField name="thumbnail"></systemField>
                    <systemField name="thumbnail">blablabla</systemField>
                    <field name="title" fieldType="string">FooBar</field>
                  </indexItem>
                </indexRequest>
      )

      val result = asyncToResult(controllers.api.Index.submit("delving")(fakeRequest))
      status(result) must equalTo(OK)

      val expected = <indexResponse>
                      <totalItemCount>1</totalItemCount>
                      <indexedItemCount>1</indexedItemCount>
                      <deletedItemCount>0</deletedItemCount>
                      <invalidItemCount>0</invalidItemCount>
                      <invalidItems />
                    </indexResponse>

      trim(XML.loadString(contentAsString(result))) must equalTo(trim(expected))

      val queryByHasDigitalObject = SolrQueryService.getSolrResponseFromServer(new SolrQuery("delving_orgId:delving delving_recordType:foo delving_hasDigitalObject:true"))

      queryByHasDigitalObject.getResults.size() must equalTo (1)
      queryByHasDigitalObject.getResults.get(0).getFirstValue("custom_title_string") must equalTo ("FooBar")
    }
  }

  "yield errors for items with invalid dates" in {
    running(FakeApplication()) {
      val fakeRequest: FakeRequest[scala.xml.NodeSeq] = FakeRequest(
        method = "POST",
        uri = "",
        headers = FakeHeaders(Map(CONTENT_TYPE -> Seq("application/xml"))),
        body =  <indexRequest>
                  <indexItem itemId="123456" itemType="test" delete="false">
                    <field name="custom:creationDate" fieldType="date">2012-05-03 15:44:28</field>
                  </indexItem>
                  <indexItem itemId="654321" itemType="test" delete="false">
                    <field name="custom:creationDate" fieldType="date">1995-12-31T23:59:59.9Z</field>
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
                        <invalidItem>
                          <error>Invalid date field 'custom:creationDate' with value '2012-05-03 15:44:28'</error>
                          <item>
                            <indexItem itemId="123456" itemType="test" delete="false">
                            <field name="custom:creationDate" fieldType="date">2012-05-03 15:44:28</field>
                            </indexItem>
                          </item>
                        </invalidItem>
                      </invalidItems>
                    </indexResponse>

      trim(XML.loadString(contentAsString(result))) must equalTo(trim(expected))

    }
  }




  step {
    running(FakeApplication()) {
      IndexingService.deleteByQuery("delving_orgId:delving delving_systemType:indexApiItem")
    }
  }

  step(cleanup)

}
