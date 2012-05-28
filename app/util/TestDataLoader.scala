package util

import com.mongodb.casbah.Imports._
import org.joda.time.DateTime
import org.bson.types.ObjectId
import java.util.Date
import models._
import eu.delving.metadata._
import play.api.Play
import play.api.Play.current
import io.Source
import controllers.SipCreatorEndPoint
import java.util.zip.GZIPInputStream
import java.io.{File, FileInputStream}
import core.processing.DataSetCollectionProcessor

/**
 * Test data
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

object TestDataLoader {

  def load() {
    if (HubUser.count() == 0) bootstrapUser()
    if (Group.count() == 0) bootstrapAccessControl()
    if (UserCollection.count() == 0) bootstrapUserCollection()
    if (DObject.count() == 0) bootstrapDObject()
    if (DataSet.count() == 0) bootstrapDatasets()
  }

  def loadDataSet() {
    val dataSet = DataSet.findBySpecAndOrgId("PrincessehofSample", "delving").get
    SipCreatorEndPoint.loadSourceData(dataSet, new GZIPInputStream(new FileInputStream(new File("conf/bootstrap/EA525DF3C26F760A1D744B7A63C67247__source.xml.gz"))))
    DataSet.updateState(dataSet, DataSetState.QUEUED)
    DataSetCollectionProcessor.process(dataSet)
  }

  private def bootstrapUser() {
    val profile = UserProfile()
    HubUser.insert(new HubUser(
      _id = new ObjectId("4e5679a80364ae80333ab939"),
      userName = "bob",
      firstName = "bob",
      lastName = "Marley",
      email = "bob@gmail.com",
      userProfile = profile
    ))
    HubUser.insert(new HubUser(
      _id = new ObjectId("4e5679a80364ae80333ab93a"),
      userName = "jimmy",
      firstName = "Jimmy",
      lastName = "Hendrix",
      email = "jimmy@gmail.com",
      userProfile = profile
    ))
    HubUser.insert(new HubUser(
      _id = new ObjectId("4e5679a80364ae80333ab93b"),
      userName = "dan",
      firstName = "Dan",
      lastName = "Brown",
      email = "dan@gmail.com",
      userProfile = profile
    ))
  }

  private def bootstrapAccessControl() {

    // all users are in delving
    HubUser.find(MongoDBObject()).foreach(u => HubUser.addToOrganization(u.userName, "delving"))

  }

  private def bootstrapUserCollection() {
    UserCollection.insert(new UserCollection(
      TS_update = new DateTime("2011-08-14T10:19:20.835Z").toDate,
      userName = "bob",
      name = "Test Collection",
      description = "A test collection",
      visibility = Visibility(10),
      thumbnail_id = None,
      thumbnail_url = None,
      deleted = false
    ))
  }

  private def bootstrapDObject() {
    DObject.insert(new DObject(
      TS_update = new DateTime("2011-08-14T10:19:20.835Z").toDate,
      userName = "bob",
      description = "A test object",
      name = "Test Object A",
      visibility = Visibility(10),
      thumbnail_id = None,
      deleted = false
    ))
    DObject.insert(new DObject(
      TS_update = new DateTime("2011-08-14T10:19:20.835Z").toDate,
      userName = "jimmy",
      description = "Another test object",
      name = "Test Object B",
      visibility = Visibility(10),
      thumbnail_id = None,
      deleted = false
    ))
  }

  private def bootstrapDatasets() {
    val factMap = new BasicDBObject()
    factMap.put("spec", "PrincesseofSample")
    factMap.put("name", "Princessehof Sample Dataset")
    factMap.put("collectionType", "all")
    factMap.put("namespacePrefix", "raw")
    factMap.put("language", "nl")
    factMap.put("country", "netherlands");
    factMap.put("provider", "Sample Man")
    factMap.put("dataProvider", "Sample Man")
    factMap.put("rights", "http://creativecommons.org/publicdomain/mark/1.0/")
    factMap.put("type", "IMAGE")

    DataSet.insert(DataSet(
      spec = "PrincessehofSample",
      userName = "bob",
      orgId = "delving",
      description = Some("Test Data"),
      state = DataSetState.ENABLED,
      visibility = Visibility(10),
      deleted = false,
      details = Details(
        name = "Princessehof Sample Dataset",
        metadataFormat = RecordDefinition(
          prefix = "raw",
          namespace = "http://delving.eu/namespaces/raw",
          schema = "http://delving.eu/namespaces/raw/schema.xsd",
          allNamespaces = List(
            Namespace(prefix="dc", uri="http://purl.org/dc/elements/1.1/", schema="http://dublincore.org/schemas/xmls/qdc/dc.xsd"),
            Namespace(prefix="dcterms", uri="http://purl.org/dc/terms/", schema="http://dublincore.org/schemas/xmls/qdc/dcterms.xsd"),
            Namespace(prefix="europeana", uri="http://www.europeana.eu/schemas/ese/", schema="http://www.europeana.eu/schemas/ese/ESE-V3.3.xsd"),
            Namespace(prefix="ese", uri="http://www.europeana.eu/schemas/ese/", schema="http://www.europeana.eu/schemas/ese/ESE-V3.3.xsd"),
            Namespace(prefix="icn", uri="http://www.icn.nl/", schema="http://www.icn.nl/schemas/ICN-V3.2.xsd"),
            Namespace(prefix="delving", uri="http://www.delving.eu/", schema="http://www.delving.eu/schemas/delving-1.0.xsd")
          ),
          isFlat = true
        ),
        facts = factMap,
        errorMessage = Some("")
      ),

      lastUploaded = new Date(0),
      idxMappings = List("icn"),
      invalidRecords = Map("icn" -> List(1)),
      mappings = Map("icn" -> Mapping(
        format = RecordDefinition.recordDefinitions.filter(rDef => rDef.prefix == "icn").head,
        recordMapping = Some(Source.fromInputStream(Play.application.resource("/bootstrap/A2098A0036EAC14E798CA3B653B96DD5__mapping_icn.xml").get.openStream()).getLines().mkString("\n"))
      )),
      formatAccessControl = Map("raw" -> FormatAccessControl(accessType = "public"), "icn" -> FormatAccessControl(accessType = "public"))
    ))
  }



}
