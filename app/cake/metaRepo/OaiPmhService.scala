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

package cake.metaRepo

import play.mvc.Http
import org.apache.log4j.Logger
import java.text.SimpleDateFormat
import java.util.Date
import xml.Elem
import models.MetadataRecord
import scala.collection.JavaConversions._
import cake.metaRepo.PmhVerbType.PmhVerb
import exceptions._

/**
 *  This class is used to parse an OAI-PMH instruction from an HttpServletRequest and return the proper XML response
 *
 *  This implementation is based on the v.2.0 specification that can be found here: http://www.openarchives.org/OAI/openarchivesprotocol.html
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since Jun 16, 2010 12:06:56 AM
 */

class OaiPmhService(request: Http.Request, accessKey: String = "") extends MetaConfig {

  private val log = Logger.getLogger(getClass);

  private val VERB = "verb"
  private val legalParameterKeys = List("verb", "identifier", "metadataPrefix", "set", "from", "until", "resumptionToken", "accessKey", "body")
  private[metaRepo] val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
  private[metaRepo] val utcDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")

  /**
   * receive an HttpServletRequest with the OAI-PMH parameters and return the correctly formatted xml as a string.
   */

  def parseRequest : String = {
    import cake.metaRepo.PmhVerbType.PmhVerb
    import models._

    if (!isLegalPmhRequest(getRequestParams(request))) return createErrorResponse("badArgument").toString

    val params = request.params.allSimple().toMap

    def pmhRequest(verb: PmhVerb) : PmhRequestEntry = createPmhRequest(params, verb)

    val response = try {
      params.get(VERB).get match {
        case "Identify" => processIdentify( pmhRequest(PmhVerbType.IDENTIFY) )
        case "ListMetadataFormats" => processListMetadataFormats( pmhRequest(PmhVerbType.List_METADATA_FORMATS) )
        case "ListSets" => processListSets( pmhRequest(PmhVerbType.LIST_SETS) )
        case "ListRecords" => processListRecords( pmhRequest(PmhVerbType.LIST_RECORDS) )
        case "ListIdentifiers" => processListIdentifiers( pmhRequest(PmhVerbType.LIST_IDENTIFIERS) )
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
    response.toString()
  }

  def isLegalPmhRequest(params: Map[String, Array[String]]) : Boolean = {

    // request must contain the verb parameter
    if (!request.params._contains(VERB)) return false

    // no repeat queryParameters are allowed
    if (params.values.exists(value => value.length > 1)) return false

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
      <request verb="Identify">{getRequestURL}</request>
      <Identify>
        <repositoryName>{repositoryName}</repositoryName>
        <baseURL>{getRequestURL}</baseURL>
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
    import models.DataSet
    // todo add checking for accessKeys and see if is valid
    val dataSets = DataSet.findAll(false)

    // when there are no collections throw "noSetHierarchy" ErrorResponse
    if (dataSets.size == 0) return createErrorResponse("noSetHierarchy")

    <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd">
     <responseDate>{currentDate}</responseDate>
     <request verb="ListSets">{getRequestURL}</request>
      <ListSets>
        { for (set <- dataSets) yield
          <set>
            <setSpec>{set.spec}</setSpec>
            <setName>{set.details.name}</setName>
          </set>
        }
      </ListSets>
    </OAI-PMH>
  }

  /**
   * This method can give back the following Error and Exception conditions: idDoesNotExist, noMetadataFormats.
   */

  def processListMetadataFormats(pmhRequestEntry: PmhRequestEntry) : Elem = {
    import models.DataSet

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
    val hasAccessKey: Boolean = pmhRequestEntry.pmhRequestItem.accessKey.isEmpty
    val metadataFormats = if (identifier.isEmpty) DataSet.getMetadataFormats(false) else DataSet.getMetadataFormats(identifierSpec, pmhRequestEntry.pmhRequestItem.accessKey)

    def formatRequest: Elem = if (!identifier.isEmpty) <request verb="ListMetadataFormats" identifier={identifier}>{getRequestURL}</request>
                                    else <request verb="ListMetadataFormats">{getRequestURL}</request>

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

  /**
   * This method can give back the following Error and Exception conditions: BadResumptionToken, cannotDisseminateFormat, noRecordsMatch, noSetHierachy
   * todo: it would be more efficient to query for only those fields (no mapping required?)
   */
  def processListIdentifiers(pmhRequestEntry: PmhRequestEntry) = {
    // parse all the params from map
    val harvestStep: HarvestStep = getHarvestStep(pmhRequestEntry)
    val setSpec = harvestStep.getPmhRequest.getSet

      <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd">
      <responseDate>{currentDate}</responseDate>
      <request verb="ListIdentifiers" from={utcFormat.format(harvestStep.getPmhRequest.getFrom)} until={(utcFormat.format(harvestStep.getPmhRequest.getUntil))}
               metadataPrefix={harvestStep.getPmhRequest.getMetadataPrefix}
               set={setSpec}>{getRequestURL}</request>
      <ListIdentifiers>
        { for (record <- harvestStep.getRecords) yield
        <header status={recordStatus(record)}>
          <identifier>{setSpec}:{record._id}</identifier>
          <datestamp>{record.modified}</datestamp>
          <setSpec>{setSpec}</setSpec>
        </header>
        }
        {renderResumptionToken(harvestStep)}
     </ListIdentifiers>
    </OAI-PMH>
  }


  def processListRecords(pmhRequestEntry: PmhRequestEntry) : Elem = {
    val harvestStep: HarvestStep = getHarvestStep(pmhRequestEntry)
    val pmhObject = harvestStep.getPmhRequest

    val elem: Elem =
      <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd">
        <responseDate>
          {currentDate}
        </responseDate>
        <request verb="ListRecords" from={printDate(pmhObject.getFrom)} until={printDate(pmhObject.getUntil)}
                 metadataPrefix={pmhObject.getMetadataPrefix}>
          {getRequestURL}
        </request>
        <ListRecords>
          {for (record <- harvestStep.getRecords) yield
          renderRecord(record, pmhObject.getMetadataPrefix, pmhObject.getSet)}{renderResumptionToken(harvestStep)}
        </ListRecords>
      </OAI-PMH>
    // todo enable later again
//    for (entry <- harvestStep.getNamespaces.toMap.entrySet()) {
//      import xml.{Null, UnprefixedAttribute}
//      elem = elem % new UnprefixedAttribute( "xmlns:"+entry.getKey.toString, entry.getValue.toString, Null )
//    }
    elem
  }

  def processGetRecord(pmhRequestEntry: PmhRequestEntry) : Elem = {
    import models.DataSet
    val pmhRequest = pmhRequestEntry.pmhRequestItem
    // get identifier and format from map else throw BadArgument Error
    if (pmhRequest.identifier.isEmpty || pmhRequest.metadataPrefix.isEmpty) return createErrorResponse("badArgument")

    val identifier = pmhRequest.identifier
    val metadataFormat = pmhRequest.metadataPrefix

    // TODO accessKey check should be done here
    val record: MetadataRecord = {
      val mdRecord = DataSet.getRecord(identifier, metadataFormat) // , pmhRequest.accessKey
      if (mdRecord == None) return createErrorResponse("noRecordsMatch")
      else mdRecord.get
    }

    val elem: Elem =
      <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd">
        <responseDate>
          {currentDate}
        </responseDate>
        <request verb="GetRecord" identifier={identifier}
                 metadataPrefix={metadataFormat}>
          {getRequestURL}
        </request>
        <GetRecord>
          {renderRecord(record, metadataFormat, identifier.split(":").head)}
        </GetRecord>
      </OAI-PMH>
    // todo enable later again
//    for (entry <- record.getNamespaces.toMap.entrySet) {
//      import xml.{Null, UnprefixedAttribute}
//      elem = elem % new UnprefixedAttribute( "xmlns:"+entry.getKey.toString , entry.getValue.toString, Null )
//    }
    elem
  }


  // todo remove this one
  val metaRepo = new MetaRepoImpl

  private def getHarvestStep(pmhRequestEntry: PmhRequestEntry) : HarvestStep = {
    if (!pmhRequestEntry.resumptionToken.isEmpty)
      metaRepo.getHarvestStep(pmhRequestEntry.resumptionToken, pmhRequestEntry.pmhRequestItem.accessKey)
    else
      createFirstHarvestStep(pmhRequestEntry.pmhRequestItem)
  }

  private def createFirstHarvestStep(item: PmhRequestItem) : HarvestStep = {
    val from = getDate(item.from)
    val until = getDate(item.until)
    metaRepo.getFirstHarvestStep(item.verb, item.set, from, until, item.metadataPrefix, item.accessKey)
  }

  private[metaRepo] def getDate(dateString: String): Date = {
    import java.text.ParseException

    if (dateString.isEmpty) return null
    val date = try {
      import java.text.ParseException
      if (dateString.matches("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z")) utcDateFormat.parse(dateString)
      else if (dateString.matches("^[0-9]{4}-[0-9]{2}-[0-9]{2}.*")) dateFormat.parse(dateString)
      else throw new ParseException("not a legal date string", 0)
    } catch {
      case e: ParseException => throw new BadArgumentException("Unable to parse date: " + dateString)
    }
    date
  }

  // todo find a way to not show status namespace when not deleted
  private def recordStatus(record: MetadataRecord) : String = if (record.deleted) "deleted" else ""

  private def renderRecord(record: MetadataRecord, metadataPrefix: String, set: String) : Elem = {

    // todo add transform into any metadata format
    val recordAsString = record.getXmlStringAsRecord(metadataPrefix).replaceAll("<[/]{0,1}(br|BR)>", "<br/>").replaceAll("&((?!amp;))","&amp;$1")
    // todo get the record separator for rendering from somewhere
    val response = try {
      import xml.XML
      val elem = XML.loadString(recordAsString)
      <record>
        <header>
          <identifier>{set}:{record._id}</identifier>
          <datestamp>{printDate(record.modified)}</datestamp>
          <setSpec>{set}</setSpec>
        </header>
        <metadata>
        {elem}
        </metadata>
      </record>
    } catch {
      case e: Exception =>
          println (e.getMessage)
          <record/>
    }
    response
  }

  private def renderResumptionToken(step: HarvestStep) = {
    if (step.hasNext)
      <resumptionToken expirationDate={printDate(step.getExpiration)} completeListSize={step.getListSize.toString}
                       cursor={step.getCursor.toString}>{step.nextResumptionToken.toString}</resumptionToken>
    else
      <resumptionToken/>
  }

  def createPmhRequest(params: Map[String, String], verb: PmhVerb): PmhRequestEntry = {
    def getParam(key: String) = params.getOrElse(key, "")

    val accessKeyParam : String = if (accessKey.isEmpty) getParam("accessKey") else accessKey

    val pmh = PmhRequestItem(
      verb,
      getParam("set"),
      getParam("from"),
      getParam("until"),
      getParam("metadataPrefix"),
      getParam("identifier"),
      accessKeyParam
      )
    PmhRequestEntry(pmh, getParam("resumptionToken"))
  }

  def getRequestURL = request.url // todo recreate getRequestURL

  def getRequestParams(request: Http.Request) = request.params.all().toMap

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
      <request>{getRequestURL}</request>
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

  case class PmhRequestItem(verb: PmhVerb, set: String, from: String, until: String, metadataPrefix: String, identifier: String, accessKey: String)
  case class PmhRequestEntry(pmhRequestItem: PmhRequestItem, resumptionToken: String)

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

  import play.Play
  def conf(key: String) = Play.configuration.getProperty(key).trim

  val repositoryName: String = conf("services.pmh.repositoryName")
  val adminEmail: String = conf("services.pmh.adminEmail")
  val earliestDateStamp: String = conf("services.pmh.earliestDateStamp")
  val repositoryIdentifier: String = conf("services.pmh.repositoryIdentifier")
  val sampleIdentifier: String = conf("services.pmh.sampleIdentifier")
}