import com.mongodb.casbah.commons.MongoDBObject
import java.io.File
import models.{ OrganizationConfiguration, HubMongoContext }
import test.Specs2TestContext
import controllers.dos._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class ThumbnailSupportSpec extends Specs2TestContext {

  "The ThumbnailSupport utility" should {

    "not duplicate thumbs, but replace them" in {

      withTestConfig { implicit configuration: OrganizationConfiguration =>
        {
          val store = HubMongoContext.fileStore(configuration)

          val sourceImage = new File("modules/dos/test/resources/dummy-object.png")

          val params = Map(
            ORGANIZATION_IDENTIFIER_FIELD -> "delving",
            COLLECTION_IDENTIFIER_FIELD -> "testSpec"
          )

          def createThumbnail() {
            TestThumbnailSupport.createAndStoreThumbnail(
              sourceImage,
              80,
              params,
              store,
              new File("/tmp"),
              onSuccess = { (width, file) => },
              onFailure = { (width, file, message) => failure("Can't create thumbnail: " + message) }
            )
          }

          val query = MongoDBObject(
            ORGANIZATION_IDENTIFIER_FIELD -> "delving",
            COLLECTION_IDENTIFIER_FIELD -> "testSpec",
            THUMBNAIL_WIDTH_FIELD -> 80,
            "filename" -> "dummy-object.png"
          )

          createThumbnail()
          store.find(query).length must equalTo(1)

          createThumbnail()
          store.find(query).length must equalTo(1)

          store.remove(query)
          store.find(query).length must equalTo(0)
        }

      }

    }

  }

  object TestThumbnailSupport extends ThumbnailSupport

}