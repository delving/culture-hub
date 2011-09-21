package extensions

import org.codehaus.jackson.map.annotate.JsonCachable
import org.bson.types.ObjectId
import org.codehaus.jackson.{JsonParser, JsonGenerator}
import org.codehaus.jackson.`type`.JavaType
import org.codehaus.jackson.map._
import java.lang.reflect.Method

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class AdditionalScalaSerializers extends Serializers.None {
  override def findSerializer(config: SerializationConfig, javaType: JavaType, beanDesc: BeanDescription, beanProp: BeanProperty) = {
    if(classOf[ObjectId].isAssignableFrom(beanDesc.getBeanClass)) {
      new ObjectIdSerializer()
    } else if(classOf[Enumeration#Value].isAssignableFrom(beanDesc.getBeanClass)) {
      new ScalaEnumerationSerializer
    } else {
      null
    }
  }
}

class AdditionalScalaDeserializers extends Deserializers.None {
  override def findBeanDeserializer(javaType: JavaType, config: DeserializationConfig, provider: DeserializerProvider, beanDesc: BeanDescription, property: BeanProperty) = {
    val clazz = javaType.getRawClass
    if(clazz == classOf[ObjectId]) {
      new ObjectIdDeserializer
    } else {
      null
    }
  }
}

@JsonCachable
class ObjectIdSerializer extends JsonSerializer[ObjectId] {
  def serialize(id: ObjectId, json: JsonGenerator, provider: SerializerProvider) {
    json.writeString(id.toString)
  }
}

class ObjectIdDeserializer extends JsonDeserializer[ObjectId] {
  def deserialize(jp: JsonParser, context: DeserializationContext) = {
    if (!ObjectId.isValid(jp.getText)) throw context.mappingException("invalid ObjectId " + jp.getText)
    new ObjectId(jp.getText)
  }
}

@JsonCachable
class ScalaEnumerationSerializer extends JsonSerializer[Enumeration#Value] {
  def serialize(enum: Enumeration#Value, json: JsonGenerator, provider: SerializerProvider) {
    // serialize by value
    json.writeString(enum.toString)
  }
}

class ScalaEnumerationDeserializer(config: DeserializationConfig, javaType: JavaType, provider: DeserializerProvider) extends JsonDeserializer[Enumeration#Value] {

  implicit def class2companion(clazz: Class[_]) = new {
    def companionClass: Class[_] = play.Play.classloader.loadClass(if (clazz.getName.endsWith("$")) clazz.getName else "%s$".format(clazz.getName))
    def companionObject = companionClass.getField("MODULE$").get(null)
  }

  def deserialize(jp: JsonParser, context: DeserializationContext) = {
    val name = jp.getText

    val clazz = javaType.getRawClass
    val companion = clazz.companionObject

    val withName: Method = {
      val ms = clazz.getDeclaredMethods
      ms.filter(_.getName == "withName").head
    }
    val enum = withName.invoke(companion, name)
    clazz.cast(enum).asInstanceOf[Enumeration#Value]
  }
}