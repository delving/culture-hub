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

package core.opendata

import java.text.SimpleDateFormat
import java.util.Date
import models._
import exceptions._
import core.search.Params
import core.opendata.PmhVerbType.PmhVerb
import play.api.Logger
import xml.{PrettyPrinter, Elem}
import org.apache.commons.lang.StringEscapeUtils
import models.{Namespace, RecordDefinition, MetadataRecord}

/**
 *  This class is used to parse an OAI-PMH instruction from an HttpServletRequest and return the proper XML response
 *
 *  This implementation is based on the v.2.0 specification that can be found here: http://www.openarchives.org/OAI/openarchivesprotocol.html
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since Jun 16, 2010 12:06:56 AM
 */

object OaiPmhService {

  val utcFormat: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
  val dateFormat = new SimpleDateFormat("yyyy-MM-dd")

  def toUtcDateTime(date: Date): String = utcFormat.format(date)

  def currentDate = toUtcDateTime(new Date())

  def printDate(date: Date): String = if (date != null) toUtcDateTime(date) else ""

}

class OaiPmhService(queryString: Map[String, Seq[String]], requestURL: String, orgId: String, accessKey: Option[String]) extends MetaConfig {

  private val log = Logger("CultureHub")
  val prettyPrinter = new PrettyPrinter(200, 5)

  private val VERB = "verb"
  private val legalParameterKeys = List("verb", "identifier", "metadataPrefix", "set", "from", "until", "resumptionToken", "accessKey", "body")
  val params = Params(queryString)

  /**
   * receive an HttpServletRequest with the OAI-PMH parameters and return the correctly formatted xml as a string.
   */

  def parseRequest : String = {

    if (!isLegalPmhRequest(params)) return createErrorResponse("badArgument").toString()

    def pmhRequest(verb: PmhVerb) : PmhRequestEntry = createPmhRequest(params, verb)

    val response: Elem = try {
      params.getValueOrElse(VERB, "error") match {
        case "Identify" => processIdentify( pmhRequest(PmhVerbType.IDENTIFY) )
        case "ListMetadataFormats" => processListMetadataFormats( pmhRequest(PmhVerbType.List_METADATA_FORMATS) )
        case "ListSets" => processListSets( pmhRequest(PmhVerbType.LIST_SETS) )
        case "ListRecords" => processListRecords( pmhRequest(PmhVerbType.LIST_RECORDS) )
        case "ListIdentifiers" => processListRecords( pmhRequest(PmhVerbType.LIST_IDENTIFIERS), true)
        case "GetRecord" => processGetRecord( pmhRequest(PmhVerbType.GET_RECORD) )
        case _ => createErrorResponse("badVerb")
      }
    }
    catch {
      case ace  : AccessKeyException => createErrorResponse("cannotDisseminateFormat", ace)
      case bae  : BadArgumentException => createErrorResponse("badArgument", bae)
      case dsnf : DataSetNotFoundException => createErrorResponse("cannotDisseminateFormat", dsnf)
      case mnf  : MappingNotFoundException => createErrorResponse("cannotDisseminateFormat", mnf)
      case rpe  : RecordParseException => createErrorResponse("cannotDisseminateFormat", rpe)
      case rtnf : ResumptionTokenNotFoundException => createErrorResponse("badResumptionToken", rtnf)
      case nrm  : RecordNotFoundException => createErrorResponse("noRecordsMatch")
      case ii   : InvalidIdentifierException => createErrorResponse("idDoesNotExist")
      case e    : Exception => createErrorResponse("badArgument", e)
    }
    prettyPrinter.format(response)
  }

  def isLegalPmhRequest(params: Params) : Boolean = {

    // request must contain the verb parameter
    if (!params._contains(VERB)) return false

    // no repeat queryParameters are allowed
    if (params.all.values.exists(value => value.length > 1)) return false

    // check for illegal queryParameter keys
    if (!(params.keys filterNot (legalParameterKeys contains)).isEmpty) return false

    true
  }

  val utcFormat: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
  private def toUtcDateTime(date: Date) : String = utcFormat.format(date)
  private def currentDate = toUtcDateTime (new Date())
  private def printDate(date: Date) : String = if (date != null) toUtcDateTime(date) else ""

  /**
   */

  def processIdentify(pmhRequestEntry: PmhRequestEntry) : Elem = {
    <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd">
      <responseDate>{currentDate}</responseDate>
      <request verb="Identify">{requestURL}</request>
      <Identify>
        <repositoryName>{repositoryName}</repositoryName>
        <baseURL>{requestURL}</baseURL>
        <protocolVersion>2.0</protocolVersion>
        <adminEmail>{adminEmail}</adminEmail>
        <earliestDatestamp>{earliestDateStamp}</earliestDatestamp>
        <deletedRecord>persistent</deletedRecord>
        <granularity>YYYY-MM-DDThh:mm:ssZ</granularity>
        <compression>deflate</compression>
        <description>
          <oai-identifier
          xmlns="http://www.openarchives.org/OAI/2.0/oai-identifier"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation=
          "http://www.openarchives.org/OAI/2.0/oai-identifier http://www.openarchives.org/OAI/2.0/oai-identifier.xsd">
            <scheme>oai</scheme>
            <repositoryIdentifier>{repositoryIdentifier}</repositoryIdentifier>
            <delimiter>:</delimiter>
            <sampleIdentifier>{sampleIdentifier}</sampleIdentifier>
          </oai-identifier>
        </description>
      </Identify>
    </OAI-PMH>
  }

  def processListSets(pmhRequestEntry: PmhRequestEntry) : Elem = {
    // todo add checking for accessKeys and see if is valid
    val collections = models.Collection.findAll(orgId, None)

    // when there are no collections throw "noSetHierarchy" ErrorResponse
    if (collections.size == 0) return createErrorResponse("noSetHierarchy")

    <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd">
      <responseDate>{currentDate}</responseDate>
      <request verb="ListSets">{requestURL}</request>
      <ListSets>
        { for (set <- collections) yield
        <set>
          <setSpec>{set.spec}</setSpec>
          <setName>{set.name}</setName>
        </set>
        }
      </ListSets>
    </OAI-PMH>
  }

  /**
   * This method can give back the following Error and Exception conditions: idDoesNotExist, noMetadataFormats.
   */

  def processListMetadataFormats(pmhRequestEntry: PmhRequestEntry) : Elem = {
    val eseSchema =
      <metadataFormat>
        <metadataPrefix>ese</metadataPrefix>
        <schema>http://www.europeana.eu/schemas/ese/ESE-V3.3.xsd</schema>
        <metadataNamespace>http://www.europeana.eu/schemas/ese/</metadataNamespace>
      </metadataFormat>

    // if no identifier present list all formats
    val identifier = pmhRequestEntry.pmhRequestItem.identifier
    val identifierSpec = identifier.split(":").head

    // otherwise only list the formats available for the identifier
    val metadataFormats = if (identifier.isEmpty) models.Collection.getAllMetadataFormats(orgId, accessKey) else models.Collection.getMetadataFormats(identifierSpec, orgId, accessKey)

    def formatRequest: Elem = if (!identifier.isEmpty) <request verb="ListMetadataFormats" identifier={identifier}>{requestURL}</request>
    else <request verb="ListMetadataFormats">{requestURL}</request>

    val elem =
      <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd">
        <responseDate>{currentDate}</responseDate>
        {formatRequest}
        <ListMetadataFormats>
          {for (format <- metadataFormats) yield
          <metadataFormat>
            <metadataPrefix>{format.prefix}</metadataPrefix>
            <schema>{format.schema}</schema>
            <metadataNamespace>{format.namespace}</metadataNamespace>
          </metadataFormat>
          }
          {if (metadataFormats.filter(_.prefix.equalsIgnoreCase("ese")).isEmpty) eseSchema}
        </ListMetadataFormats>
      </OAI-PMH>
    elem
  }

  def processListRecords(pmhRequestEntry: PmhRequestEntry, idsOnly: Boolean = false) : Elem = {

    val setName = pmhRequestEntry.getSet
    if(setName.isEmpty) throw new BadArgumentException("No set provided")
    val metadataFormat = pmhRequestEntry.getMetadataFormat
    val collection: models.Collection = models.Collection.findBySpecAndOrgId(setName, orgId).getOrElse(throw new DataSetNotFoundException("unable to find set: " + setName))
    if(!models.Collection.getMetadataFormats(collection.spec, collection.orgId, accessKey).exists(f => f.prefix == metadataFormat)) {
      throw new MappingNotFoundException("Format %s unknown".format(metadataFormat));
    }
    val (records, totalValidRecords) = collection.getRecords(metadataFormat, pmhRequestEntry.getLastTransferIdx, pmhRequestEntry.recordsReturned)

    val recordList = records.toList
    // FIXME these head calls blow up if there are no records
    val from = printDate(recordList.head.modified)
    val to = printDate(recordList.last.modified)

    val elem: Elem = if (!idsOnly) {
      <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd">
        <responseDate>
          {currentDate}
        </responseDate>
        <request verb="ListRecords" from={from} until={to}
                 metadataPrefix={metadataFormat}>
          {requestURL}
        </request>
        <ListRecords>
          {for (record <- recordList) yield
          renderRecord(record, metadataFormat, setName)}
          {pmhRequestEntry.renderResumptionToken(recordList, totalValidRecords)}
        </ListRecords>
      </OAI-PMH>
    }
    else {
      <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd">
        <responseDate>{currentDate}</responseDate>
        <request verb="ListIdentifiers" from={from} until={to}
                 metadataPrefix={metadataFormat}
                 set={setName}>{requestURL}</request>
        <ListIdentifiers>
          { for (record <- recordList) yield
          <header status={recordStatus(record)}>
            <identifier>{orgId}:{setName}:{record._id}</identifier>
            <datestamp>{record.modified}</datestamp>
            <setSpec>{setName}</setSpec>
          </header>
          }
          {pmhRequestEntry.renderResumptionToken(recordList, totalValidRecords)}
        </ListIdentifiers>
      </OAI-PMH>
    }
  
      prependNamespaces(metadataFormat, collection, elem)


  }

  def processGetRecord(pmhRequestEntry: PmhRequestEntry) : Elem = {
    val pmhRequest = pmhRequestEntry.pmhRequestItem
    // get identifier and format from map else throw BadArgument Error
    if (pmhRequest.identifier.isEmpty || pmhRequest.metadataPrefix.isEmpty) return createErrorResponse("badArgument")

    val identifier = pmhRequest.identifier
    val metadataFormat = pmhRequest.metadataPrefix

    val record: MetadataRecord = {
      val mdRecord = MetadataRecord.getMDR(identifier, metadataFormat, accessKey)
      if (mdRecord == None) return createErrorResponse("noRecordsMatch")
      else mdRecord.get
    }

    val collection = models.Collection.findBySpecAndOrgId(identifier.split(":")(1), identifier.split(":")(0)).get

    val elem: Elem =
      <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd">
        <responseDate>
          {currentDate}
        </responseDate>
        <request verb="GetRecord" identifier={identifier}
                 metadataPrefix={metadataFormat}>
          {requestURL}
        </request>
        <GetRecord>
          {renderRecord(record, metadataFormat, identifier.split(":")(1))}
        </GetRecord>
      </OAI-PMH>

    prependNamespaces(metadataFormat, collection, elem)
  }

  // todo find a way to not show status namespace when not deleted
  private def recordStatus(record: MetadataRecord) : String = if (record.deleted) "deleted" else ""

  private def renderRecord(record: MetadataRecord, metadataPrefix: String, set: String) : Elem = {

    val recordAsString = "<record>%s</record>".format(record.getCachedTransformedRecord(metadataPrefix).getOrElse("")).replaceAll("<[/]{0,1}(br|BR)>", "<br/>").replaceAll("&((?!amp;))","&amp;$1")
    // todo get the record separator for rendering from somewhere
    val response = try {
      import xml.XML
      val elem: Elem = XML.loadString(StringEscapeUtils.unescapeHtml(recordAsString))
      <record>
        <header>
          <identifier>{record.pmhId}</identifier>
          <datestamp>{printDate(record.modified)}</datestamp>
          <setSpec>{set}</setSpec>
        </header>
        <metadata>
          {if (metadataPrefix != "ese")
            elem
          else
            <record>{elem.child.filterNot(_.prefix == "delving")}</record>
            }
        </metadata>
      </record>
    } catch {
      case e: Exception =>
        println (e.getMessage)
          <record/>
    }
    response
  }
  
  private def prependNamespaces(metadataFormat: String, collection: Collection, elem: Elem): Elem = {

    var mutableElem = elem

    val formatNamespaces = RecordDefinition.recordDefinitions.find(r => r.prefix == metadataFormat).get.allNamespaces
    val globalNamespaces = collection.namespaces.map(ns => Namespace(ns._1, ns._2, ""))

    for (ns <- (formatNamespaces ++ globalNamespaces)) {
      import xml.{Null, UnprefixedAttribute}
      mutableElem = mutableElem % new UnprefixedAttribute("xmlns:" + ns.prefix, ns.uri, Null)
    }
    mutableElem
  }
  

  def createPmhRequest(params: Params, verb: PmhVerb): PmhRequestEntry = {
    def getParam(key: String) = params.getValueOrElse(key, "")

    val pmh = PmhRequestItem(
      verb,
      getParam("set"),
      getParam("from"),
      getParam("until"),
      getParam("metadataPrefix"),
      getParam("identifier")
    )
    PmhRequestEntry(pmh, getParam("resumptionToken"))
  }

  /**
   * This method is used to create all the OAI-PMH error responses to a given OAI-PMH request. The error descriptions have
   * been taken directly from the specifications document for v.2.0.
   */
  def createErrorResponse(errorCode: String, exception : Exception): Elem = {
    log.error(errorCode, exception)
    createErrorResponse(errorCode)
  }

  def createErrorResponse(errorCode: String): Elem = {
    <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd">
      <responseDate>{currentDate}</responseDate>
      <request>{requestURL}</request>
      {errorCode match {
      case "badArgument" => <error code="badArgument">The request includes illegal arguments, is missing required arguments, includes a repeated argument, or values for arguments have an illegal syntax.</error>
      case "badResumptionToken" => <error code="badResumptionToken">The value of the resumptionToken argument is invalid or expired.</error>
      case "badVerb" => <error code="badVerb">Value of the verb argument is not a legal OAI-PMH verb, the verb argument is missing, or the verb argument is repeated.</error>
      case "cannotDisseminateFormat" => <error code="cannotDisseminateFormat">The metadata format identified by the value given for the metadataPrefix argument is not supported by the item or by the repository.</error>
      case "idDoesNotExist" => <error code="idDoesNotExist">The value of the identifier argument is unknown or illegal in this repository.</error>
      case "noMetadataFormats" => <error code="noMetadataFormats">There are no metadata formats available for the specified item.</error>
      case "noRecordsMatch" => <error code="noRecordsMatch">The combination of the values of the from, until, set and metadataPrefix arguments results in an empty list.</error>
      case "noSetHierarchy" => <error code="noSetHierarchy">This repository does not support sets or no sets are publicly available for this repository.</error> // Should never be used. We only use sets
      case _ => <error code="unknown">Unknown Error Corde</error> // should never happen.
    }}
    </OAI-PMH>
  }

  case class PmhRequestItem(verb: PmhVerb, set: String, from: String, until: String, metadataPrefix: String, identifier: String)

  case class PmhRequestEntry(pmhRequestItem: PmhRequestItem, resumptionToken: String) {

    val recordsReturned = 250

    private val ResumptionTokenExtractor = """(.+?):(.+?):(.+?):(.+?):(.+)""".r

    lazy val ResumptionTokenExtractor(set, metadataFormat, recordInt, pageNumber, originalSize) = resumptionToken // set:medataFormat:lastTransferIdx:numberSeen
    
    def getSet = if (resumptionToken.isEmpty) pmhRequestItem.set else set
    def getMetadataFormat = if (resumptionToken.isEmpty) pmhRequestItem.metadataPrefix else metadataFormat
    def getPagenumber = if (resumptionToken.isEmpty) 1 else pageNumber.toInt
    def getLastTransferIdx = if (resumptionToken.isEmpty) 0 else recordInt.toInt
    def getOriginalListSize = if (resumptionToken.isEmpty) 0 else originalSize.toInt
    
    def renderResumptionToken(recordList: List[MetadataRecord], totalListSize: Int) = {

      val nextLastIdx = recordList.last.transferIdx.get

      val originalListSize = if (getOriginalListSize == 0) totalListSize else getOriginalListSize

      val currentPageNr = if (resumptionToken.isEmpty) getPagenumber else getPagenumber + 1

      val nextResumptionToken = "%s:%s:%s:%s:%s".format(getSet, getMetadataFormat, nextLastIdx, currentPageNr, originalListSize)

      val cursor = currentPageNr * recordsReturned
      if (cursor < originalListSize) {
        <resumptionToken expirationDate={printDate(new Date())} completeListSize={(originalListSize).toString}
                         cursor={cursor.toString}>{nextResumptionToken}</resumptionToken>
      }
      else
          <resumptionToken/>
    }

  }

}

object PmhVerbType extends Enumeration {

  case class PmhVerb(command: String) extends Val(command)

  val LIST_SETS = PmhVerb("ListSets")
  val List_METADATA_FORMATS = PmhVerb("ListMetadataFormats")
  val LIST_IDENTIFIERS = PmhVerb("ListIdentifiers")
  val LIST_RECORDS = PmhVerb("ListRecords")
  val GET_RECORD = PmhVerb("GetRecord")
  val IDENTIFY = PmhVerb("Identify")
}

trait MetaConfig {

  import play.api.Play
  import play.api.Play.current
  def conf(key: String) = Play.configuration.getString(key).getOrElse("").trim

  val repositoryName: String = conf("services.pmh.repositoryName")
  val adminEmail: String = conf("services.pmh.adminEmail")
  val earliestDateStamp: String = conf("services.pmh.earliestDateStamp")
  val repositoryIdentifier: String = conf("services.pmh.repositoryIdentifier")
  val sampleIdentifier: String = conf("services.pmh.sampleIdentifier")
}