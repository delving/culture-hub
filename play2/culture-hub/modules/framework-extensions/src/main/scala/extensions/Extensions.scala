package extensions

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

import org.bson.types.ObjectId
import org.codehaus.jackson.map.annotate.JsonCachable
import org.codehaus.jackson.map.module.SimpleModule
import org.codehaus.jackson.map.{JsonSerializer, SerializerProvider, JsonDeserializer, DeserializationContext}
import org.codehaus.jackson.{Version, JsonGenerator, JsonParser}
import play.api.mvc.Results.Status
import com.codahale.jerkson.Json._
import play.api.PlayException
import play.api.mvc.{JavascriptLitteral, PathBindable}

/**
 * Framework extensions
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Extensions {

  def Json(data: AnyRef, status: Int = 200) = Status(status)(generate(data)).as("application/json")

}

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

object JJson extends com.codahale.jerkson.Json {
  val module = new SimpleModule("JerksonJson", Version.unknownVersion())
  module.addSerializer(classOf[ObjectId], new ObjectIdSerializer)
  module.addDeserializer(classOf[ObjectId], new ObjectIdDeserializer)
  mapper.registerModule(module)
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

// ~~~ Exceptions

object ProgrammerException {
  def apply(message: String) = PlayException("Programmer Exception", message)
}

object ConfigurationException {
  def apply(message: String) = PlayException("Configuration Exception", message)
}
