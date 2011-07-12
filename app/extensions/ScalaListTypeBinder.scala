package extensions

import play.data.binding.TypeBinder
import java.lang.annotation.Annotation
import java.lang.reflect.Type
import play.mvc.Http.Request

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class ScalaListTypeBinder extends TypeBinder[scala.collection.immutable.List[String]] {
  def bind(name: String, annotations: Array[Annotation], value: String, actualClass: Class[_], genericType: Type): List[String] = {
    val values:Array[String] = Request.current().params.getAll(name)
    values.toList
  }
}