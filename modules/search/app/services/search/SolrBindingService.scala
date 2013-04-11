package services.search

import scala.collection.JavaConversions._
import org.apache.solr.client.solrj.response.{ FacetField, QueryResponse }
import collection.immutable.{ HashMap, Map => ImMap }
import org.apache.solr.client.solrj.response.FacetField.Count
import collection.mutable.{ ListBuffer, Map }
import org.apache.solr.common.SolrDocumentList
import java.lang.{ Boolean => JBoolean, Float => JFloat }
import java.util.{ Date, ArrayList, List => JList, Map => JMap }
import models.MetadataAccessors
import play.api.Logger
import xml.{ XML, Elem }
import org.apache.commons.lang.StringEscapeUtils
import org.apache.solr.common.util.NamedList

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 10 /18/10 9:01 PM
 */

object SolrBindingService {

  def stripDynamicFieldLabels(fieldName: String): String = {
    if (fieldName.split("_").length > 2)
      fieldName.replaceFirst("_(string|facet|location|int|single|text|date|link|s|lowercase|geohash)$", "").replaceFirst("^(facet|sort|sort_all)_", "")
    else
      fieldName
  }

  def addFieldNodes(key: String, values: List[Any]): List[FieldValueNode] =
    for (value <- values; if value != null) yield (FieldValueNode(key, value.toString))

  def getSolrDocumentList(queryResponse: QueryResponse): List[SolrResultDocument] = {
    val highLightMap: JMap[String, JMap[String, JList[String]]] = queryResponse.getHighlighting
    val responseParams = queryResponse.getResponseHeader.get("params").asInstanceOf[NamedList[Any]]
    val isGroup = responseParams.indexOf("group", 1) > -1 && responseParams.get("group").toString.toBoolean

    if (isGroup) {
      queryResponse.getGroupResponse.getValues.flatMap { gc =>
        {
          val groupField = gc.getName
          val matches = gc.getMatches
          val nGroups = gc.getNGroups
          gc.getValues.flatMap { group =>
            {
              val groupValue = group.getGroupValue
              val numFound = group.getResult.getNumFound

              val groupInfo = GroupInfo(groupField, matches, nGroups, groupValue, numFound)
              getSolrDocumentList(group.getResult, Some(groupInfo), highLightMap)
            }
          }
        }
      }.toList
    } else {
      getSolrDocumentList(queryResponse.getResults, None, highLightMap)
    }
  }

  def getSolrDocumentList(documentList: SolrDocumentList, groupInfo: Option[GroupInfo] = None, highLightMap: JMap[String, JMap[String, JList[String]]] = null): List[SolrResultDocument] = {
    import java.lang.{ Integer => JInteger }

    val docs = new ListBuffer[SolrResultDocument]
    val ArrayListObject = classOf[ArrayList[Any]]
    val StringObject = classOf[String]
    val DateObject = classOf[Date]
    val FloatObject = classOf[JFloat]
    val BooleanObject = classOf[JBoolean]
    val IntegerObject = classOf[JInteger]
    // check for required fields else check exception

    documentList.foreach {
      doc =>
        val solrDoc = SolrResultDocument(groupInfo = groupInfo)
        doc.entrySet.filter(!_.getKey.endsWith("_facet")).foreach {
          field =>
            val normalisedField = stripDynamicFieldLabels(field.getKey)
            val FieldValueClass: Class[_] = field.getValue.getClass
            FieldValueClass match {
              case ArrayListObject => solrDoc.add(normalisedField, addFieldNodes(normalisedField, field.getValue.asInstanceOf[ArrayList[Any]].toList))
              case StringObject | DateObject | BooleanObject | FloatObject | IntegerObject =>
                solrDoc.add(normalisedField, List(FieldValueNode(normalisedField, field.getValue.toString)))
              case _ => println("unknown class in SolrBindingService " + normalisedField + FieldValueClass.getCanonicalName)
            }
        }
        val id = solrDoc getFirst ("id")
        if (highLightMap != null && highLightMap.containsKey(id)) {
          highLightMap.get(id).filterNot(_._1.endsWith("_facet")).foreach(entry => solrDoc addHighLightField (entry._1, entry._2.toList))
        }
        docs add solrDoc
    }
    docs.toList
  }

  def getBriefDocs(queryResponse: QueryResponse): List[BriefDocItem] = {
    getSolrDocumentList(queryResponse).map(doc => BriefDocItem(doc))
  }

  def getBriefDocs(documentList: SolrDocumentList): List[BriefDocItem] = {
    getSolrDocumentList(documentList, None).map(doc => BriefDocItem(doc))
  }

  def createFacetMap(links: List[SOLRFacetQueryLinks]) = FacetMap(links.toList)

  def createFacetStatistics(facets: List[FacetField]) = FacetStatisticsMap(facets.toList)
}

case class FacetMap(private val links: List[SOLRFacetQueryLinks]) {

  val facetMap = Map[String, SOLRFacetQueryLinks]()
  links.foreach {
    facet =>
      facetMap put (facet.getType, facet)
  }

  def getFacetList = links

  def getFacet(key: String): SOLRFacetQueryLinks = facetMap.getOrElse(key, SOLRFacetQueryLinks("unknown"))
}

case class FacetStatisticsMap(private val facets: List[FacetField]) {

  val facetsMap = Map[String, List[FacetField.Count]]()
  facets.foreach {
    facet =>
      if (facet.getValueCount != 0) facetsMap put (facet.getName, facet.getValues.toList)
  }

  def facetExists(key: String): Boolean = facetsMap.containsKey(key)

  def availableFacets: List[String] = facetsMap.keys.toList

  private def getDummyFacetField: FacetField = {
    val facetField = new FacetField("unknown")
    facetField.add("nothing", 0)
    facetField
  }

  def getFacetValueCount(key: String, facetName: String) = {
    val count: Count = getFacet(facetName).filter(fc => fc.getName == key).headOption.getOrElse(new FacetField.Count(getDummyFacetField, "unknown", 0))
    count.getCount
  }

  def getFacetCount(key: String) = facets.filter(ff => ff.getName == key).headOption.getOrElse(getDummyFacetField).getValueCount

  def getFacet(key: String): List[FacetField.Count] = facetsMap.getOrElse(key, getDummyFacetField.getValues.toList)

}

case class SolrResultDocument(
    fieldMap: Map[String, List[FieldValueNode]] = Map[String, List[FieldValueNode]](),
    highLightMap: Map[String, List[String]] = Map[String, List[String]](),
    groupInfo: Option[GroupInfo]) {

  def get(field: String): List[String] = for (node: FieldValueNode <- fieldMap.getOrElse(field, List[FieldValueNode]())) yield node.fieldValue

  def getFieldValueNode(field: String): List[FieldValueNode] = fieldMap.getOrElse(field, List[FieldValueNode]())

  def getFieldValueNodeGroupedByLanguage(field: String): ImMap[String, List[FieldValueNode]] = fieldMap.getOrElse(field, List[FieldValueNode]()).groupBy(fvn => fvn.getLanguage)

  def getFirst(field: String): String = fieldMap.getOrElse(field, List[FieldValueNode]()).headOption.getOrElse(FieldValueNode("", "")).fieldValue

  private[search] def add(field: String, value: scala.List[FieldValueNode]) = fieldMap.put(field, value)

  private[search] def addHighLightField(fieldName: String, values: List[String]) = highLightMap.put(fieldName, values)

  private[search] def getFieldNames = fieldMap.keys

  /** only retrieve fields of the kind prefix_value **/
  def getFieldValueList: List[FieldValue] = for (key <- fieldMap.keys.toList.filter(_.matches(".*_.*"))) yield FieldValue(key, this)

  def getHighLightsAsFieldValueList: List[FieldValue] = for (key <- highLightMap.keys.toList) yield FieldValue(key, this)

  def getFieldValuesFiltered(include: Boolean, fields: List[String]): List[FieldValue] = getFieldValueList.filter((fv => fields.contains(fv.getKey) == include))

}

/** in case of a group response, give useful information about which group this document belongs to **/
case class GroupInfo(
  groupField: String,
  matches: Int,
  nGroups: Int,
  groupValue: String,
  numFound: Long)

case class FieldFormatted(key: String, values: Array[String]) {
  def getKey: String = key
  def getKeyAsMessageKey = "_metadata.%s" format (key.replaceFirst("_", "."))
  def getValues: Array[String] = values
  def getValuesFormatted(separator: String = ";&#160;"): String = values.mkString(separator)
  def isNotEmpty: Boolean = !values.isEmpty
}

case class FieldValue(key: String, solrDocument: SolrResultDocument) {

  private val fieldValues = solrDocument.get(key)
  private val highLightValues: Option[List[String]] = solrDocument.highLightMap.get(key)

  /**
   * This gives back the key that was used to retrieve the fields from the SolrResultDocument
   */
  def getKey = key

  /**
   * This gives back the key that was used to retrieve the fields from the SolrResultDocument, but now replacing the "_" convention
   * used by solr to ":" so that it can be used in xml tags or to represented the fieldnames as they were before being indexed
   * by Apache Solr
   */
  def getKeyAsXml = key.replaceFirst("_", ":")

  /**
   * This gives back the key formatted as a metadata key as specified in the message.properties files.
   */
  def getKeyAsMessageKey = "_metadata.%s" format (key.replaceFirst("_", "."))

  /**
   * Only give back the first item from the fieldMap retrieved with 'key' in the SolrResultDocument as a String. When the key
   * is not found an empty String is returned.
   */
  def getFirst: String = solrDocument.getFirst(key)

  /**
   * Give back all values found in the fieldMap retrieved with 'key' in the SolrResultDocument as a String Array. When the
   * key is not found an empty String Array is returned.
   */
  def getValueAsArray: Array[String] = fieldValues.asInstanceOf[List[String]].toArray

  def getHighLightValuesAsArray: Array[String] = highLightValues.getOrElse(List.empty).asInstanceOf[List[String]].toArray

  /**
   * Give back all values found in the fieldMap retrieved with 'key' in the SolrResultDocument as a Formatted String. When the
   * key is not found an empty String is returned.
   */

  def getArrayAsString(separator: String = ";&#160;"): String = fieldValues.mkString(separator)

  /**
   * This function gives back a boolean to say if the results returned from the fieldMap in the SolrResultDocument will be empty or not
   */
  def isNotEmpty = fieldValues.length != 0

  def hasHighLights = if (highLightValues != None) true else false

  def asCDATA(text: String) = "<![CDATA[%s]]>".format(text)

}

case class FieldValueNode(fieldName: String, fieldValue: String, attributes: ImMap[String, String] = new HashMap[String, String]()) {

  def getFieldName = fieldName

  def getFieldValue = fieldValue

  def getAttribute(key: String) = attributes.getOrElse(key, "")

  def getLanguage = attributes.getOrElse("xml:lang", "unknown")

  def hasLanguageAttribute = attributes.contains("xml:lang")

  def hasAttributes = !attributes.isEmpty

  def getAttributeKeys = attributes.keys
}

case class SolrDocId(solrDocument: SolrResultDocument) {
  def getEuropeanaUri: String = solrDocument.getFirst("europeana_uri")
}

case class BriefDocItem(solrDocument: SolrResultDocument) extends MetadataAccessors {

  protected def assign(key: String) = solrDocument.getFirst(key)

  protected def values(key: String): List[String] = getFieldValue(key).getValueAsArray.toList

  def getFieldValue(key: String): FieldValue = FieldValue(key, solrDocument)

  protected def getFieldValuesFiltered(include: Boolean, fields: Seq[String]): List[FieldValue] = solrDocument.getFieldValuesFiltered(include, fields.toList)

  def getAsString(key: String): String = assign(key)

  def getHighlights: List[FieldValue] = solrDocument.getHighLightsAsFieldValueList

  var index: Int = _
  var fullDocUrl: String = _

  // debug and scoring information
  var score: Int = _
  var debugQuery: String = _

  protected def filteredAndOrderedFields(filteredFields: Seq[String]) = {
    getFieldValuesFiltered(include = false, filteredFields).sortWith((fv1, fv2) => fv1.getKey < fv2.getKey)
  }

  // todo clean up and make more dry
  def toKmFields(filteredFields: Seq[String] = Seq.empty, simpleData: Boolean = true, context: SearchContext): List[Elem] = {

    val renderedFields = filteredAndOrderedFields(filteredFields).map(field => SolrQueryService.renderKMLSimpleDataFields(field, simpleData, context))

    val (fields, fieldErrors) = (renderedFields.flatMap(f => f._1), renderedFields.flatMap(f => f._2))

    fieldErrors.foreach { e =>
      Logger("CultureHub").warn(
        "Couldn't parse value %s for field %s: %s".format(
          e._1, e._2, e._3
        ), e._3)
    }

    fields
  }

  def toXml(filteredFields: Seq[String] = Seq.empty, context: SearchContext): Elem = {

    val renderedFields = filteredAndOrderedFields(filteredFields).map(field => SolrQueryService.renderXMLFields(field, context))

    val (fields, fieldErrors) = (renderedFields.flatMap(f => f._1), renderedFields.flatMap(f => f._2))

    val renderedHighlights = getHighlights.map(field => SolrQueryService.renderHighLightXMLFields(field))
    val (highlights, highlighErrors) = (renderedHighlights.flatMap(f => f._2), renderedHighlights.flatMap(f => f._2))

    fieldErrors.foreach { e =>
      Logger("CultureHub").warn(
        "Couldn't parse value %s for field %s: %s".format(
          e._1, e._2, e._3
        ), e._3)
    }

    highlighErrors.foreach { e =>
      Logger("CultureHub").warn(
        "Couldn't parse highlight value %s for field %s: %s".format(
          e._1, e._2, e._3
        ), e._3)
    }

    <item>
      <fields>{ fields }</fields>{
        if (getHighlights.isEmpty) <highlights/>
        else
          <highlights>{ highlights }</highlights>
      }
    </item>
  }

  def toJson(filteredFields: Seq[String] = Seq.empty, context: SearchContext) = {
    val renderedFields = filteredAndOrderedFields(filteredFields).map { fv =>
      (fv.getKey -> (fv.getValueAsArray.map { value =>
        SolrQueryService.prependImageCacheUrl(fv.getKeyAsXml, value, context)
      }))
    }

    ListMap("item" ->
      ListMap("fields" ->
        ListMap(renderedFields.toSeq: _*)
      )
    )
  }

}