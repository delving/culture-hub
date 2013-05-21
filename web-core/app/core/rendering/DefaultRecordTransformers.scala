package core.rendering

import xml.{ Node, NodeSeq, Elem }
import xml.transform.{ RuleTransformer, RewriteRule }

/**
 * Default transformers
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 *
 */
object DefaultRecordTransformers {

  lazy val transformers = Seq(geoHashRecordTransformer)

  private val geoHashRecordTransformer = new RecordTransformer {

    def transformRecord(record: NodeSeq, context: RenderingContext): NodeSeq = {
      if (context.parameters.get("pt") != None) {
        filterGeoTags(record, "delving:geoHash", context.parameters.getOrElse("pt", Seq("0,0")).head)
      } else {
        record
      }
    }

    private def filterGeoTags(record: NodeSeq, fieldName: String, crd: String): NodeSeq = {
      val removeIt = new RewriteRule {
        override def transform(n: Node): NodeSeq = n match {
          case e: Elem if "%s:%s".format(e.prefix, e.label) == fieldName && e.text != crd => NodeSeq.Empty
          case _ => n
        }
      }

      new RuleTransformer(removeIt).transform(record)
    }

  }

}