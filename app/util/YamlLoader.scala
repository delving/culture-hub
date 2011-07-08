package util

import scala.collection.immutable.List
import collection.mutable.Buffer

/**
 * Loader for Yaml files, based on the play-scala loader. Adds improved support for type conversion
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object YamlLoader {

  def load[T](name: String)(implicit m: ClassManifest[T]) = {

    val yamlParser = new org.yaml.snakeyaml.Yaml(
      new org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor(classOf[Object], play.Play.classloader) {

        import org.yaml.snakeyaml.nodes._

        override def constructObject(node: Node) = {
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
            case n: SequenceNode if n.getType.getName == "scala.collection.immutable.List" => {
              import scala.collection.JavaConversions._

              val buffer: Buffer[Any] = for (node <- n.getValue) yield {
                node.asInstanceOf[ScalarNode].getValue
              }
              buffer.toList
            }
            case _ => super.constructObject(node)
          }
        }
      })
    yamlParser.setBeanAccess(org.yaml.snakeyaml.introspector.BeanAccess.FIELD)

    import scala.collection.JavaConversions._

    m.erasure.getName match {
      case "scala.collection.immutable.List" => play.test.Fixtures.loadYaml(name, yamlParser).asInstanceOf[java.util.List[Any]].toList.asInstanceOf[T]
      case "scala.collection.immutable.Map" => play.test.Fixtures.loadYaml(name, yamlParser).asInstanceOf[java.util.Map[Any, Any]].toMap[Any, Any].asInstanceOf[T]
      case _ => play.test.Fixtures.loadYaml(name, yamlParser).asInstanceOf[T]
    }
  }

}