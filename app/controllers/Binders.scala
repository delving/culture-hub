package controllers

import org.bson.types.ObjectId
import play.api.mvc.{JavascriptLitteral, PathBindable}

object Binders {

  implicit def bindableObjectId = new PathBindable[ObjectId] {
    def bind(key: String, value: String) = {
      if (ObjectId.isValid(value)) {
        Right(new ObjectId(value))
      } else {
        Left("Cannot parse parameter " + key + " as BSON ObjectId")
      }
    }

    def unbind(key: String, value: ObjectId) = value.toString
  }
  
  implicit def bindableJavascriptLitteral = new JavascriptLitteral[ObjectId] {
    def to(value: ObjectId) = value.toString
  }

}