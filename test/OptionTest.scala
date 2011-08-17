import controllers.user.Admin.GroupModel
import extensions.{PlayParameterNameReader, ObjectIdSerializer}
import net.liftweb.json._
import net.liftweb.json.Serialization._

object OptionTest extends Application {

  implicit val formats = new DefaultFormats {
    override val parameterNameReader = PlayParameterNameReader
  } + new ObjectIdSerializer


// implicit val formats = DefaultFormats + new ObjectIdSerializer

// class ObjectIdSerializer extends Serializer[ObjectId] {
//   private val Class = classOf[ObjectId]
//
//   def deserialize(implicit format: Formats) = {
//     case (TypeInfo(Class, _), json) => json match {
//       case JObject(JField("id", JString(s)) :: Nil) => new ObjectId(s)
//       case x => throw new MappingException("Can't convert " + x + " to ObjectId")
//     }
//   }
//
//   def serialize(implicit format: Formats) = {
//     case x: ObjectId => JObject(JField("id", JString(x.toString)) :: Nil)
//   }
// }

 val ser = write(GroupModel(None, name = "joe", members = Nil))
 println(ser)
 println(read[GroupModel]("{\"name\":\"abc\",\"readRight\":false,\"updateRight\":true,\"deleteRight\":false,\"members\":[]}"))
}
