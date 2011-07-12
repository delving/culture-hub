package extensions

import play.data.binding.TypeBinder
import java.lang.annotation.Annotation
import java.lang.reflect.Type
import play.mvc.Http.Request
import org.bson.types.ObjectId

/**
 * Custom Play type binders for types we need to be bound form the view
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class ScalaListTypeBinder extends TypeBinder[scala.collection.immutable.List[String]] {
  def bind(name: String, annotations: Array[Annotation], value: String, actualClass: Class[_], genericType: Type): List[String] = {
    val values:Array[String] = Request.current().params.getAll(name)
    values.toList
  }
}

class ObjectIdTypeBinder extends TypeBinder[ObjectId] {
  def bind(name: String, annotations: Array[Annotation], value: String, actualClass: Class[_], genericType: Type) = {
    new ObjectId(value)
  }
}