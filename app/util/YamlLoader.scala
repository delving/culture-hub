package util

import collection.mutable.Buffer

/**
 * Loader for Yaml files, based on the play-scala loader. Adds improved support for type conversion.
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

    val constructor = new org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor(classOf[Object], play.Play.classloader) {

      import org.yaml.snakeyaml.nodes._

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

        node match {
          case n: ScalarNode if n.getTag.getClassName == "None" => None
          case n: ScalarNode if n.getTag.getClassName == "Some[String]" => Some(n.getValue)
          case n: ScalarNode if n.getTag.getClassName == "Some[Long]" => Some(java.lang.Long.parseLong(n.getValue, 10))
          case n: ScalarNode if n.getTag.getClassName == "Some[Int]" => Some(java.lang.Integer.parseInt(n.getValue, 10))
          case n: ScalarNode if n.getTag.getClassName == "NotAssigned" => play.db.anorm.NotAssigned
          case n: ScalarNode if n.getTag.getClassName == "Id[String]" => play.db.anorm.Id(n.getValue)
          case n: ScalarNode if n.getTag.getClassName == "Id[Long]" => play.db.anorm.Id(java.lang.Long.parseLong(n.getValue, 10))
          case n: ScalarNode if n.getTag.getClassName == "Id[Int]" => play.db.anorm.Id(java.lang.Integer.parseInt(n.getValue, 10))
          case n: ScalarNode if n.getTag.getClassName == "Option[String]" => Some(n.getValue)

          // additional support starts here
          case n: ScalarNode if n.getType.getName == "scala.Option" => Option(n.getValue)
          case n: MappingNode if n.getType.getName == "scala.Option" => Option(n.getValue)
          case n: SequenceNode if n.getType.getName == "scala.collection.immutable.List" => {
            import scala.collection.JavaConversions._

            val buffer: Buffer[Any] = for (node <- n.getValue) yield {
              node match {
                case n: Node if n.isInstanceOf[ScalarNode] => node.asInstanceOf[ScalarNode].getValue
                case n: Node if n.isInstanceOf[MappingNode] => super.constructObject(n)
                case n: Node => throw new RuntimeException("Not yet implemented ==> " + n.getClass)
              }

            }
            buffer.toList
          }
          case n: MappingNode if n.getType.getName == "scala.collection.immutable.Map" => {
            import scala.collection.JavaConversions._
            val map = for (node <- n.getValue) yield {
              val keyNode = node.asInstanceOf[NodeTuple].getKeyNode
              val valueNode = node.asInstanceOf[NodeTuple].getValueNode

              val value = valueNode match {
                case v: Node if v.isInstanceOf[ScalarNode] => valueNode.asInstanceOf[ScalarNode].getValue
                case v: Node if v.isInstanceOf[MappingNode] => super.constructObject(v)
                case v: Node => throw new RuntimeException("Not yet implemented ==> " + v.getClass)
              }
              (keyNode.asInstanceOf[ScalarNode].getValue, value)
            }
            map.toMap
          }
          case n: ScalarNode if n.getType.getName == "[B" => n.getValue.getBytes("UTF-8")
          case n: MappingNode if n.getType.getName == "java.lang.Object" && n.getTag.getClassName.contains("models.") => {
            // yadaaaaaaaaaaa java-scala generics conversion stupidity
            val theType: Class[AnyRef] = getClassForName(n.getTag.getClassName).asInstanceOf[Class[AnyRef]]
            n.setType(theType)
            super.constructObject(n)
          }
          case _ => super.constructObject(node)
        }
      }
    }

    // this code is here for future reference should we one day need to dive into this madness again.
    //
    // so far, I found no better way to make both SnakeYaml and Salat happy about Object type construction. (Salat will complain that a property needs to have a default value, hence be
    // declared as an option, if it is queried for somewhere)
    // this means every time we have an Option(anotherCaseClass) we need to tell SnakeYaml how to build it. it also makes the YAML code look funny.

    //    val referenceDescription = new TypeDescription(classOf[models.User])
    //    referenceDescription.putMapPropertyType("reference", classOf[String], classOf[scala.Option[models.UserReference]])
    //    constructor.addTypeDescription(referenceDescription)

    val yamlParser = new org.yaml.snakeyaml.Yaml(constructor)
    yamlParser.setBeanAccess(org.yaml.snakeyaml.introspector.BeanAccess.FIELD)

    import scala.collection.JavaConversions._

    m.erasure.getName match {
      case "scala.collection.immutable.List" => play.test.Fixtures.loadYaml(name, yamlParser).asInstanceOf[java.util.List[Any]].toList.asInstanceOf[T]
      case "scala.collection.immutable.Map" => play.test.Fixtures.loadYaml(name, yamlParser).asInstanceOf[java.util.Map[Any, Any]].toMap[Any, Any].asInstanceOf[T]
      case _ => play.test.Fixtures.loadYaml(name, yamlParser).asInstanceOf[T]
    }
  }

}