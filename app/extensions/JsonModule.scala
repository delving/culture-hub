package extensions

import org.bson.types.ObjectId
import org.codehaus.jackson.{JsonToken, JsonParser, JsonGenerator}
import org.codehaus.jackson.map._
import annotate.JsonCachable

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

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