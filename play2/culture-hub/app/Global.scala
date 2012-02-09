/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

import com.mongodb.BasicDBObject
import core.mapping.MappingService
import java.util.Date
import models._
import org.bson.types.ObjectId
import org.joda.time.DateTime
import play.api._
import libs.Crypto
import play.api.Play.current
import util.{MissingLibs, ThemeHandler}

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    println("""
              ____       __      _
             / __ \___  / /   __(_)___  ____ _
            / / / / _ \/ / | / / / __ \/ __ `/
           / /_/ /  __/ /| |/ / / / / / /_/ /
          /_____/\___/_/ |___/_/_/ /_/\__, /
                                     /____/
             ______      ____                  __  __      __
            / ____/_  __/ / /___  __________  / / / /_  __/ /_
           / /   / / / / / __/ / / / ___/ _ \/ /_/ / / / / __ \
          / /___/ /_/ / / /_/ /_/ / /  /  __/ __  / /_/ / /_/ /
          \____/\__,_/_/\__/\__,_/_/   \___/_/ /_/\__,_/_.___/
    """)

    // load themes
    ThemeHandler.startup()

    if (Play.isDev || Play.isTest) {
      if (User.count() == 0) bootstrapUser()
      if (UserCollection.count() == 0) bootstrapUserCollection()
      if (DObject.count() == 0) bootstrapDObject()
      if (DataSet.count() == 0) bootstrapDatasets()
    }

    MappingService.init()

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
    var factMap = new BasicDBObject()
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
      idxMappings = List("icn")
    ))
  }
}