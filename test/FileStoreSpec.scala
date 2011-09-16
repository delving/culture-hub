import com.mongodb.casbah.commons.MongoDBObject
import controllers.FileStore
import controllers.user.FileUpload
import java.io.{ByteArrayInputStream, File}
import models.StoredFile
import org.bson.types.ObjectId
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.ShouldMatchers
import play.data.Upload
import play.libs.{MimeTypes, IO}
import play.mvc.results.{RenderBinary, Result}
import play.test.UnitFlatSpec
import util.TestDataGeneric

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class FileStoreSpec extends UnitFlatSpec with ShouldMatchers with TestDataGeneric with BeforeAndAfterAll {

  override protected def afterAll() {
    FileUpload.fs.db.dropDatabase()
  }

  val testFile = new File(play.Play.applicationPath, "public/images/dummy-object.png")
  val TEST_UID = "123456789"
  val TEST_OID = new ObjectId

  var uploaded_id: ObjectId = null

  it should "upload a file" in {
    val upload = new MockUpload(testFile)
    val uploads = List(upload)

    val res: Result = FileUpload.uploadFile(TEST_UID, uploads)
    res.getClass should not equal (classOf[Error])
  }

  it should "find back files by upload UID" in {
    val fetched: Seq[StoredFile] = FileUpload.fetchFilesForUID(TEST_UID)
    fetched.length should equal(1)
    fetched.head.name should equal(testFile.getName)
    fetched.head.length should equal(testFile.length())
  }

  it should "attach uploaded files to an object, given an upload UID and an object ID" in {
    FileUpload.markFilesAttached(TEST_UID, TEST_OID)
    val file = FileUpload.fs.findOne(MongoDBObject(FileStore.OBJECT_POINTER_FIELD -> TEST_OID))
    file should not equal (None)
    FileUpload.fetchFilesForUID(TEST_UID).length should equal(0)
    uploaded_id = file.get.get("_id").get.asInstanceOf[ObjectId]
  }

  it should "mark an active thumbnail and an active image given a file pointer and object ID" in {
    FileUpload.activateThumbnail(uploaded_id, TEST_OID)

    val image = FileStore.getImage(TEST_OID.toString, false)
    val thumbnail = FileStore.getImage(TEST_OID.toString, true)

    image.getClass should equal (classOf[RenderBinary])
    thumbnail.getClass should equal (classOf[RenderBinary])
  }


}

class MockUpload(file: File) extends Upload {
  def asBytes() = IO.readContent(file)

  def asStream() = new ByteArrayInputStream(asBytes())

  def getContentType = MimeTypes.getContentType(file.getName)

  def getFileName = file.getName

  def getFieldName = "mock"

  def getSize = file.length()

  def isInMemory = true

  def asFile() = file
}
