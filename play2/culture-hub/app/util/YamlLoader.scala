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

import collection.mutable.Buffer
import extensions.ProgrammerException
import org.yaml.snakeyaml.nodes._
import com.mongodb.BasicDBObject
import org.yaml.snakeyaml.Yaml
import play.api.Play.current

/**
 * Loader for Yaml files, based on the play-scala loader but meant for real-life usage. Adds improved support for type conversion.
 *
 * Rules for working on this class should something go wrong:
 * - make sure the data in mongo is empty
 * - make sure the data in the right mongo database is empty (culturehub when running in dev mode and culturehub-TEST when running in dev mode)
 * - triple-check how you wrote the YAML mapping
 * - if you need to influence object construction FIRST look at the data you get from the commented out stuff and only then if that is not enough to do a proper matching debug into SnakeYAML
 *
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object YamlLoader {

  def load[T](name: String)(implicit m: ClassManifest[T]) = {

    val constructor = new org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor(classOf[Object], play.api.Play.classloader) {

      class MoreComplicated extends Exception

      override def constructObject(node: Node) = {

//        println()
//        println(node.getType)
//        println(node.getType.getName)
//        println(node.toString)
//        println("Tag")
//        println(node.getTag.getClass)
//        println(node.getTag.getClassName)
//        println(node.getTag.getLength)
//        println(node.getTag.getValue)
//        println()

        try {
          constructScalarObject(node)
        } catch {
          case complicated: MoreComplicated => constructMoreComplicated(node)
          case t => throw new RuntimeException(t)
        }

      }

      def constructScalarObject(node: Node) = {
        node match {
          case n: ScalarNode if n.getTag.getClassName == "None" => None
          case n: ScalarNode if n.getTag.getClassName == "Some[String]" => Some(n.getValue)
          case n: ScalarNode if n.getTag.getClassName == "Some[Long]" => Some(java.lang.Long.parseLong(n.getValue, 10))
          case n: ScalarNode if n.getTag.getClassName == "Some[Int]" => Some(java.lang.Integer.parseInt(n.getValue, 10))

          case n: ScalarNode if n.getTag.getClassName == "ObjectId" => new org.bson.types.ObjectId(n.getValue)

          // TODO probably we also need to do this for other scalar types
          case n: ScalarNode if n.getType.getName == "scala.Option" && n.getTag.getClassName == "bool" => Some(n.getValue.toBoolean)
          case n: ScalarNode if n.getTag.getClassName == "Option[String]" => Some(n.getValue)
          case n: ScalarNode if n.getType.getName == "scala.Option" => Option(n.getValue)
          case _ => throw new MoreComplicated()
        }
      }

      def constructMoreComplicated(node: Node): AnyRef = {
        node match {
          case n: MappingNode if n.getType.getName == "scala.Option" => Option(n.getValue)
          case n: SequenceNode if n.getType.getName == "scala.collection.immutable.List" => {
            import scala.collection.JavaConversions._

            val buffer: Buffer[Any] = for (node <- n.getValue) yield {
              node match {
                case n: Node if n.isInstanceOf[ScalarNode] => node.asInstanceOf[ScalarNode].getValue
                case n: Node if n.isInstanceOf[MappingNode] => constructObject(n)
                case n: Node => throw new RuntimeException("Not yet implemented ==> " + n.getClass)
              }

            }
            buffer.toList
          }
          case n: MappingNode if n.getType.getName == "scala.collection.immutable.Map" => constructMap(n)
          case n: MappingNode if n.getType.getName == "com.mongodb.BasicDBObject" => {
            import scala.collection.JavaConversions._
            new BasicDBObject(constructMap(n))
          }
          case n: ScalarNode if n.getType.getName == "[B" => n.getValue.getBytes("UTF-8")
          case n: MappingNode if n.getType.getName == "java.lang.Object" && n.getTag.getClassName.contains("models.") => {
            // yadaaaaaaaaaaa java-scala generics conversion stupidity
            val theType: Class[AnyRef] = getClassForName(n.getTag.getClassName).asInstanceOf[Class[AnyRef]]
            n.setType(theType)
            constructObject(n)
          }
          case _ => super.constructObject(node)
        }
      }

      def constructMap(n: MappingNode) = {
        import scala.collection.JavaConversions._
        val map = for (node <- n.getValue) yield {
          val keyNode = node.asInstanceOf[NodeTuple].getKeyNode
          val valueNode = node.asInstanceOf[NodeTuple].getValueNode

          val value = valueNode match {
            case v: Node if v.isInstanceOf[ScalarNode] => valueNode.asInstanceOf[ScalarNode].getValue
            case v: Node if v.isInstanceOf[MappingNode] => constructObject(v)
            case v: Node if v.isInstanceOf[SequenceNode] => constructMoreComplicated(v)
            case v: Node => throw new RuntimeException("Not yet implemented ==> " + v.getClass)
          }
          (keyNode.asInstanceOf[ScalarNode].getValue, value)
        }
        map.toMap
      }

    }

    val yamlParser = new Yaml(constructor)
    yamlParser.setBeanAccess(org.yaml.snakeyaml.introspector.BeanAccess.FIELD)

    def loadYaml(name: String, yaml: Yaml) {
      try {
        val is = play.api.Play.current.resourceAsStream(name).getOrElse(throw ProgrammerException("Could not find YAML file " + name))
        yaml.load(is)
      } catch {
        case t => throw new RuntimeException("Problem reading YAML from " + name, t)
      }
    }

    import scala.collection.JavaConversions._

    m.erasure.getName match {
      case "scala.collection.immutable.List" => loadYaml(name, yamlParser).asInstanceOf[java.util.List[Any]].toList.asInstanceOf[T]
      case "scala.collection.immutable.Map" => loadYaml(name, yamlParser).asInstanceOf[java.util.Map[Any, Any]].toMap[Any, Any].asInstanceOf[T]
      case _ => loadYaml(name, yamlParser).asInstanceOf[T]
    }
  }
}