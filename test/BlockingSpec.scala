/*
 * Copyright 2012 Delving B.V.
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

import com.mongodb.casbah.commons.MongoDBObject
import models.{User, DObject}
import org.scalatest.matchers.ShouldMatchers
import play.mvc.Http.Response
import play.mvc.Scope.Session
import play.test.{FunctionalTest, UnitFlatSpec}
import util.TestDataGeneric
import scala.collection.JavaConversions._

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class BlockingSpec extends UnitFlatSpec with ShouldMatchers with TestDataGeneric {

  it should "not find blocked objects" in {
    val dobject = DObject.findOne(MongoDBObject("name" -> "A test object")).get

    val req = getAuthenticated
    req.method = "GET"
    val response: Response = FunctionalTest.GET(req, "/bob/object/%s".format(dobject._id.toString))
    response.status should be (200)

    val blockReq = getAuthenticated
    blockReq.method = "POST"
    val response1: Response = FunctionalTest.POST(blockReq, "/bob/object/%s/block".format(dobject._id.toString))
    response1.status should be (200)

    // now it should not be here
    val tryToGet = getAuthenticated
    tryToGet.method = "GET"

    val response2: Response = FunctionalTest.GET(tryToGet, "/bob/object/%s".format(dobject._id.toString))
    response2.status should be (404)
  }

  it should "totally block a user and its artifacts" in {
    val blockReq = getAuthenticated
    blockReq.method = "POST"
    val response1: Response = FunctionalTest.POST(blockReq, "/jimmy/block")
    response1.status should be (200)


    // the user should not be logged in, we can check this by asserting that one of the session values isn't there
    Session.current().clear()
    val login = FunctionalTest.POST("/login", Map("username" -> "jimmy", "password" -> "secret"))
    login.status should be (200)
    val req = FunctionalTest.newRequest()
    req.cookies = login.cookies
    Session.current().get("connectedUserId") should be (null)


    DObject.findByUser("jimmy").length should be (0)

  }


  def getAuthenticated = {
    val login = FunctionalTest.POST("/login", Map("username" -> "bob", "password" -> "secret"))
    val req = FunctionalTest.newRequest()
    req.cookies = login.cookies
    req
  }


}