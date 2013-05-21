package core

import eu.delving.MappingResult
import scala.collection.JavaConverters._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
package object processing {

  type MultiMap = Map[String, List[String]]

  implicit def listMapToScala(map: java.util.Map[String, java.util.List[String]]) = map.asScala.map(v => (v._1, v._2.asScala.toList)).toMap

  class RichMappingResult(mappingResult: MappingResult) {

    def getFields: MultiMap = mappingResult.fields()

    def getSearchFields: MultiMap = mappingResult.searchFields()

    def getCopyFields: MultiMap = {
      mappingResult.copyFields().asScala.map(f => (f._1.replaceAll(":", "_") -> f._2.asScala.toList)).toMap[String, List[String]]
    }

    def getSystemFields: MultiMap = {
      getCopyFields.filter(f => SystemField.isValid(f._1))
    }

    def getOtherFields: MultiMap = {
      getCopyFields.filterNot(f => SystemField.isValid(f._1))

    }

  }

  implicit def mappingResultToRichMappingResult(mappingResult: MappingResult): RichMappingResult = new RichMappingResult(mappingResult)

}