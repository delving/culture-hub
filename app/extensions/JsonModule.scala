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
    json.writeStartObject()
    json.writeFieldName("id")
    json.writeString(id.toString)
    json.writeEndObject()
  }
}

class ObjectIdDeserializer extends JsonDeserializer[ObjectId] {
  def deserialize(jp: JsonParser, context: DeserializationContext) = {
    if (jp.getCurrentToken == JsonToken.START_OBJECT) jp.nextToken()
    if (jp.getCurrentToken != JsonToken.FIELD_NAME) throw context.mappingException("id expected")
    jp.nextToken()
    if (!ObjectId.isValid(jp.getText)) throw context.mappingException("invalid ObjectId " + jp.getText)
    new ObjectId(jp.getText)
  }
}