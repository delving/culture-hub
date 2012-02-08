/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

import core.mapping.MappingService
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

    if (Play.mode == Mode.Dev) {
      if (User.count() == 0) bootstrapUser()
      if (UserCollection.count() == 0) bootstrapUserCollection()
      if (DObject.count() == 0) bootstrapDObject()
      // todo: MetadataRecords
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
}