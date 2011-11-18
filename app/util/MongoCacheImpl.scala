package util

import play.cache.CacheImpl
import models.salatContext._
import scala.collection.JavaConversions.asJavaMap
import com.mongodb.casbah.Imports._
import com.mongodb.WriteResult
import java.io._

/**
 * Antwerp -> Rotterdam implementation of a MongoCache for Play
 *
 * TODO -> expiration
 * TODO -> incr, decr
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class MongoCacheImpl extends CacheImpl {
  
  val K = "key"
  val V = "value"
  val E = "expiration"

  val cache = connection("mongoCache")

  def add(key: String, value: AnyRef, expiration: Int) {
    cache.insert(MongoDBObject(K -> key, V -> ser(value), E -> expiration))
  }

  def safeAdd(key: String, value: AnyRef, expiration: Int) = {
    val writeResult: WriteResult = cache.insert(MongoDBObject(K -> key, V -> ser(value), E -> expiration), WriteConcern.Safe)
    writeResult.getLastError.ok()
  }

  def set(key: String, value: AnyRef, expiration: Int) {
    cache.update(MongoDBObject(K -> key), MongoDBObject(V -> ser(value), E -> expiration), true, false)
  }

  def safeSet(key: String, value: AnyRef, expiration: Int) = {
    val writeResult: WriteResult = cache.update(MongoDBObject(K -> key), MongoDBObject(V -> ser(value), E -> expiration), true, false, WriteConcern.Safe)
    writeResult.getLastError.ok()
  }

  def replace(key: String, value: AnyRef, expiration: Int) {
    cache.update(MongoDBObject(K -> key), MongoDBObject(V -> ser(value), E -> expiration))
  }

  def safeReplace(key: String, value: AnyRef, expiration: Int) = {
    val writeResult: WriteResult = cache.update(MongoDBObject(K -> key), MongoDBObject(V -> ser(value), E -> expiration), false, false, WriteConcern.Safe)
    writeResult.getLastError.ok()
  }


  def get(key: String) = cache.findOne(MongoDBObject(K -> key)) match {
    case None => null
    case Some(dbo) => deser(dbo.get(V).asInstanceOf[Array[Byte]])
  }

  def get(keys: Array[String]) = cache.find(K $in (keys)).map(dbo => (dbo.get(K).toString -> deser(dbo.get(V).asInstanceOf[Array[Byte]]))).toMap[String, AnyRef]

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

  def ser(o: AnyRef) = {
    val baos = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(baos)
    oos.writeObject(o.asInstanceOf[Serializable])
    baos.close()
    baos.toByteArray
  }

  def deser(o: Array[Byte]) = {
    val bais = new ByteArrayInputStream(o)
    val ois = new ObjectInputStream(bais)
    ois.readObject()
  }

}