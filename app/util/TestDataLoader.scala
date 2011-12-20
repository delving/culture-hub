/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package util

import models.salatContext._
import com.mongodb.casbah.Imports._
import models._
import play.libs.Crypto
import controllers.{SolrServer, AccessControl}

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

/**
 * Generic TestData set-up. This only makes sure the test database is empty at the beginning of the test run
 */
trait TestData {
  // clean everything up when we start
  connection.getCollectionNames() foreach {
    collection =>
      connection.getCollection(collection).remove(MongoDBObject())
  }
  commonsConnection.getCollectionNames() foreach {
    collection =>
      commonsConnection.getCollection(collection).remove(MongoDBObject())
  }

  SolrServer.deleteFromSolrByQuery("*:*")

}

trait TestDataGeneric extends TestData {
  YamlLoader.load[List[Any]]("testData.yml").foreach {
    _ match {
      case u: User => User.insert(u.copy(password = play.libs.Crypto.passwordHash(u.password, Crypto.HashType.SHA512)))
      case d: DataSet => DataSet.insert(d)
      case dobject: DObject => DObject.insert(dobject)
      case ucol: UserCollection => UserCollection.insert(ucol)
      case md: MetadataRecord => {
        val ds = DataSet.findBySpecAndOrgId("Verzetsmuseum", "delving").get
        DataSet.getRecords(ds).insert(md)
      }
      case _ =>
    }
  }

  val delving = Organization(node = "cultureHub", orgId = "delving", name = Map("en" -> "Delving"))
  val bnf = Organization(node = "cultureHub", orgId = "bnf", name = Map("en" -> "National Library of France", "fr" -> "Bibliothèque nationale de France"))

  val delvingId = Organization.insert(delving)
  val bnfId = Organization.insert(bnf)

  // all users are in delving
  User.find(MongoDBObject()).foreach(u => Organization.addUser("delving", u.userName))

  val delvingOwners = Group(node = "cultureHub", name = "Owners", orgId = delving.orgId, grantType = GrantType.OWN)
  val delvingOwnersId = Group.insert(delvingOwners)

  // bob is an owner
  Group.addUser("bob", delvingOwnersId.get)
}

class TestDataLoader extends TestDataGeneric