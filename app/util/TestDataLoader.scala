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
    implicit val configuration = DomainConfigurationHandler.getDefaultConfiguration.get
    if (HubUser.dao.count() == 0) bootstrapUser()
    if (Group.dao.count() == 0) bootstrapAccessControl()
    if (DataSet.dao.count() == 0) bootstrapDatasets()
  }

  def loadDataSet() {
    implicit val configuration = DomainConfigurationHandler.getDefaultConfiguration.get
    val dataSet = DataSet.dao.findBySpecAndOrgId("PrincessehofSample", "delving").get
    SipCreatorEndPoint.loadSourceData(dataSet, new GZIPInputStream(new FileInputStream(new File("conf/bootstrap/EA525DF3C26F760A1D744B7A63C67247__source.xml.gz"))))
    DataSet.dao.updateState(dataSet, DataSetState.QUEUED)
    DataSetCollectionProcessor.process(dataSet)
  }

  private def bootstrapUser() {
    val profile = UserProfile()
    HubUser.dao("delving").insert(new HubUser(
      _id = new ObjectId("4e5679a80364ae80333ab939"),
      userName = "bob",
      firstName = "bob",
      lastName = "Marley",
      email = "bob@gmail.com",
      userProfile = profile
    ))
    HubUser.dao("delving").insert(new HubUser(
      _id = new ObjectId("4e5679a80364ae80333ab93a"),
      userName = "jimmy",
      firstName = "Jimmy",
      lastName = "Hendrix",
      email = "jimmy@gmail.com",
      userProfile = profile
    ))
    HubUser.dao("delving").insert(new HubUser(
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
    HubUser.dao("delving").find(MongoDBObject()).foreach(u => HubUser.dao("delving").addToOrganization(u.userName, "delving"))

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

    DataSet.dao("delving").insert(DataSet(
      spec = "PrincessehofSample",
      userName = "bob",
      orgId = "delving",
      state = DataSetState.ENABLED,
      deleted = false,
      details = Details(
        name = "Princessehof Sample Dataset",
        facts = factMap
      ),
      idxMappings = List("icn"),
      invalidRecords = Map("icn" -> List(1)),
      mappings = Map("icn" -> Mapping(
        format = RecordDefinition.recordDefinitions.filter(rDef => rDef.prefix == "icn").head,
        recordMapping = Some(Source.fromInputStream(Play.application.resource("/bootstrap/A2098A0036EAC14E798CA3B653B96DD5__mapping_icn.xml").get.openStream()).getLines().mkString("\n"))
      )),
      formatAccessControl = Map("icn" -> FormatAccessControl(accessType = "public"))
    ))
  }



}
