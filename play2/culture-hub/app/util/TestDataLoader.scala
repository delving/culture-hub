package util

import com.mongodb.casbah.Imports._
import org.joda.time.DateTime
import org.bson.types.ObjectId
import java.util.Date
import models._
import extensions.MissingLibs

/**
 * Test data
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

object TestDataLoader {

  def load() {
    if (User.count() == 0) bootstrapUser()
    if (Group.count() == 0) bootstrapAccessControl()
    if (UserCollection.count() == 0) bootstrapUserCollection()
    if (DObject.count() == 0) bootstrapDObject()
    if (DataSet.count() == 0) bootstrapDatasets()
  }


  private def bootstrapUser() {
    val profile = UserProfile()
    User.insert(new User(
      _id = new ObjectId("4e5679a80364ae80333ab939"),
      userName = "bob",
      firstName = "bob",
      lastName = "Marley",
      email = "bob@gmail.com",
      password = MissingLibs.passwordHash("secret", MissingLibs.HashType.SHA512),
      userProfile = profile,
      isActive = true,
      isHubAdmin = Some(true),
      nodesAdmin = List("culturehub")
    ))
    User.insert(new User(
      _id = new ObjectId("4e5679a80364ae80333ab93a"),
      userName = "jimmy",
      firstName = "Jimmy",
      lastName = "Hendrix",
      email = "jimmy@gmail.com",
      password = MissingLibs.passwordHash("secret", MissingLibs.HashType.SHA512),
      userProfile = profile,
      isActive = true
    ))
    User.insert(new User(
      _id = new ObjectId("4e5679a80364ae80333ab93b"),
      userName = "dan",
      firstName = "Dan",
      lastName = "Brown",
      email = "dan@gmail.com",
      password = MissingLibs.passwordHash("secret", MissingLibs.HashType.SHA512),
      userProfile = profile,
      isActive = true
    ))
  }

  private def bootstrapAccessControl() {

    val delving = Organization(node = "cultureHub", orgId = "delving", name = Map("en" -> "Delving"))
    val bnf = Organization(node = "cultureHub", orgId = "bnf", name = Map("en" -> "National Library of France", "fr" -> "Bibliotheque nationale de France"))

    val delvingId = Organization.insert(delving)
    val bnfId = Organization.insert(bnf)

    // all users are in delving
    User.find(MongoDBObject()).foreach(u => Organization.addUser("delving", u.userName))

    val delvingOwners = Group(node = "cultureHub", name = "Owners", orgId = delving.orgId, grantType = GrantType.OWN.key)
    val delvingOwnersId = Group.insert(delvingOwners)

    // bob is an owner
    Group.addUser("bob", delvingOwnersId.get)

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
      user_id = new ObjectId("4e5679a80364ae80333ab939"),
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
          accessKeyRequired = true
        ),
        facts = factMap,
        errorMessage = Some("")
      ),
      lastUploaded = new Date(0),
      idxMappings = List("icn"),
      invalidRecords = Map("icn" -> List(1)),
      mappings = Map("icn" -> Mapping(format = RecordDefinition.recordDefinitions.filter(rDef => rDef.prefix == "icn").head))
    ))
  }

}
