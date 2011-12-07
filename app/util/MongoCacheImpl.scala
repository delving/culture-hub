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

import play.cache.CacheImpl
import models.salatContext._
import scala.collection.JavaConversions.asJavaMap
import com.mongodb.casbah.Imports._
import com.mongodb.WriteResult
import java.io._
import play.jobs.{Every, Job}
import controllers.ErrorReporter

/**
 * Antwerp -> Rotterdam implementation of a MongoCache for Play
 *
 * TODO -> incr, decr
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class MongoCacheImpl extends CacheImpl {
  
  val K = "k"
  val V = "v"
  val E = "e"

  val cache = connection("mongoCache")
  cache.ensureIndex(MongoDBObject(K -> 1))

  def add(key: String, value: AnyRef, expiration: Int) {
    cache.insert(MongoDBObject(K -> key, V -> ser(value), E -> expirationTime(expiration)))
  }

  def safeAdd(key: String, value: AnyRef, expiration: Int) = {
    val writeResult: WriteResult = cache.insert(MongoDBObject(K -> key, V -> ser(value), E -> expirationTime(expiration)), WriteConcern.Safe)
    writeResult.getLastError.ok()
  }

  def set(key: String, value: AnyRef, expiration: Int) {
    cache.update(MongoDBObject(K -> key), MongoDBObject(K -> key, V -> ser(value), E -> expirationTime(expiration)), true, false)
  }

  def safeSet(key: String, value: AnyRef, expiration: Int) = {
    val writeResult: WriteResult = cache.update(MongoDBObject(K -> key), MongoDBObject(K -> key, V -> ser(value), E -> expirationTime(expiration)), true, false, WriteConcern.Safe)
    writeResult.getLastError.ok()
  }

  def replace(key: String, value: AnyRef, expiration: Int) {
    cache.update(MongoDBObject(K -> key) ++ E $gt (System.currentTimeMillis()), MongoDBObject(V -> ser(value), E -> expirationTime(expiration)))
  }

  def safeReplace(key: String, value: AnyRef, expiration: Int) = {
    val writeResult: WriteResult = cache.update(MongoDBObject(K -> key) ++ E $gt (System.currentTimeMillis()), MongoDBObject(V -> ser(value), E -> expirationTime(expiration)), false, false, WriteConcern.Safe)
    writeResult.getLastError.ok()
  }

  def get(key: String) = cache.findOne(MongoDBObject(K -> key) ++ E $gt (System.currentTimeMillis())) match {
    case None => null
    case Some(dbo) => deser(dbo.get(V).asInstanceOf[Array[Byte]])
  }

  def get(keys: Array[String]) = cache.find(K $in (keys) ++ E $gt (System.currentTimeMillis())).map(dbo => (dbo.get(K).toString -> deser(dbo.get(V).asInstanceOf[Array[Byte]]))).toMap[String, AnyRef]

  def incr(key: String, by: Int) = 0L

  def decr(key: String, by: Int) = 0L

  def clear() {
    cache.drop()
  }

  def delete(key: String) {
    cache.remove(MongoDBObject(K -> key))
  }

  def safeDelete(key: String) = {
    val writeResult: WriteResult = cache.remove(MongoDBObject(K -> key), WriteConcern.Safe)
    writeResult.getLastError.ok()
  }

  def stop() {}

  private def expirationTime(seconds: Int) = seconds * 1000 + System.currentTimeMillis()

  private def ser(o: AnyRef) = {
    val baos = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(baos)
    oos.writeObject(o.asInstanceOf[Serializable])
    oos.flush()
    baos.close()
    baos.toByteArray
  }

  private def deser(o: Array[Byte]) = {
    val bais = new ByteArrayInputStream(o)
    val ois = new ObjectInputStream(bais)
    val daObject = ois.readObject()
    ois.close()
    daObject
  }

}

@Every("10min")
class MongoCacheCleaner extends Job {
  override def doJob() {
    val cache = connection("mongoCache")
    cache.remove("e" $lt (System.currentTimeMillis()))
  }

  override def onException(e: Throwable) {
    ErrorReporter.reportError(getClass.getName, e, "Error cleaning mongoCache")
  }
}