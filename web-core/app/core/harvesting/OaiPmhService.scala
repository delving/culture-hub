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

package core.harvesting

import core.{ HarvestCollectionLookupService, HubId }
import java.text.SimpleDateFormat
import java.util.Date
import exceptions._
import core.harvesting.PmhVerbType.PmhVerb
import play.api.Logger
import xml._
import java.net.URLEncoder
import core.Constants._
import models._
import core.collection.{ OrganizationCollectionMetadata, Harvestable }
import com.escalatesoft.subcut.inject.{ Injectable, BindingModule }
import models.MetadataItem
import models.Namespace
import xml.NamespaceBinding
import collection.mutable.ArrayBuffer

/**
 *  This class is used to parse an OAI-PMH instruction and returns the proper XML response
 *
 *  This implementation is based on the v.2.0 specification that can be found here: http://www.openarchives.org/OAI/openarchivesprotocol.html
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @since Jun 16, 2010 12:06:56 AM
 */
class OaiPmhService(queryString: Map[String, Seq[String]], requestURL: String, orgId: String, format: Option[String], accessKey: Option[String])(implicit configuration: OrganizationConfiguration, val bindingModule: BindingModule) extends Injectable {

  private val log = Logger("CultureHub")
  val prettyPrinter = new PrettyPrinter(300, 5)

  val harvestCollectionLookupService = inject[HarvestCollectionLookupService]

  private val VERB = "verb"
  private val legalParameterKeys = List("verb", "identifier", "metadataPrefix", "set", "from", "until", "resumptionToken", "accessKey", "body")

  /**
   * receive an HttpServletRequest with the OAI-PMH parameters and return the correctly formatted xml as a string.
   */

  def parseRequest: String = {

    if (!isLegalPmhRequest(queryString)) return createErrorResponse("badArgument").toString()

    def pmhRequest(verb: PmhVerb): PmhRequestEntry = createPmhRequest(queryString, verb)

    val response: Elem = try {
      queryString.get(VERB).map(_.head).getOrElse("error") match {
        case "Identify" => processIdentify(pmhRequest(PmhVerbType.IDENTIFY))
        case "ListMetadataFormats" => processListMetadataFormats(pmhRequest(PmhVerbType.List_METADATA_FORMATS))
        case "ListSets" => processListSets(pmhRequest(PmhVerbType.LIST_SETS))
        case "ListRecords" => processListRecords(pmhRequest(PmhVerbType.LIST_RECORDS))
        case "ListIdentifiers" => processListRecords(pmhRequest(PmhVerbType.LIST_IDENTIFIERS), true)
        case "GetRecord" => processGetRecord(pmhRequest(PmhVerbType.GET_RECORD))
        case _ => createErrorResponse("badVerb")
      }
    } catch {
      case ace: AccessKeyException => createErrorResponse("cannotDisseminateFormat", ace)
      case bae: BadArgumentException => createErrorResponse("badArgument", bae)
      case dsnf: DataSetNotFoundException => createErrorResponse("noRecordsMatch", dsnf)
      case mnf: MappingNotFoundException => createErrorResponse("cannotDisseminateFormat", mnf)
      case rpe: RecordParseException => createErrorResponse("cannotDisseminateFormat", rpe)
      case rtnf: ResumptionTokenNotFoundException => createErrorResponse("badResumptionToken", rtnf)
      case nrm: RecordNotFoundException => createErrorResponse("noRecordsMatch")
      case ii: InvalidIdentifierException => createErrorResponse("idDoesNotExist")
      case e: Exception => createErrorResponse("badArgument", e)
    }
    //    prettyPrinter.format(response) // todo enable pretty printing later again
    response.toString()
  }

  def isLegalPmhRequest(params: Map[String, Seq[String]]): Boolean = {

    // request must contain the verb parameter
    if (!params.contains(VERB)) return false

    // no repeat queryParameters are allowed
    if (params.values.exists(value => value.length > 1)) return false

    // check for illegal queryParameter keys
    if (!(params.keys filterNot (legalParameterKeys contains)).isEmpty) return false

    // check for validity of dates
    Seq(params.get("from").headOption, params.get("until").headOption).filterNot(_.isEmpty).foreach { date =>
      try {
        OaiPmhService.dateFormat.parse(date.get.head)
      } catch {
        case t: Throwable => {
          try {
            OaiPmhService.utcFormat.parse(date.get.head)
          } catch {
            case t: Throwable => return false
          }
        }
      }
    }

    true
  }

  def processIdentify(pmhRequestEntry: PmhRequestEntry): Elem = {
    <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd">
      <responseDate>{ OaiPmhService.currentDate }</responseDate>
      <request verb="Identify">{ requestURL }</request>
      <Identify>
        <repositoryName>{ configuration.oaiPmhService.repositoryName }</repositoryName>
        <baseURL>{ requestURL }</baseURL>
        <protocolVersion>2.0</protocolVersion>
        <adminEmail>{ configuration.oaiPmhService.adminEmail }</adminEmail>
        <earliestDatestamp>{ configuration.oaiPmhService.earliestDateStamp }</earliestDatestamp>
        <deletedRecord>no</deletedRecord>
        <granularity>YYYY-MM-DDThh:mm:ssZ</granularity>
        <compression>deflate</compression>
        <description>
          <oai-identifier xmlns="http://www.openarchives.org/OAI/2.0/oai-identifier" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/oai-identifier http://www.openarchives.org/OAI/2.0/oai-identifier.xsd">
            <scheme>oai</scheme>
            <repositoryIdentifier>{ configuration.oaiPmhService.repositoryIdentifier }</repositoryIdentifier>
            <delimiter>:</delimiter>
            <sampleIdentifier>{ configuration.oaiPmhService.sampleIdentifier }</sampleIdentifier>
          </oai-identifier>
        </description>
      </Identify>
    </OAI-PMH>
  }

  def processListSets(pmhRequestEntry: PmhRequestEntry): Elem = {

    val collections = harvestCollectionLookupService.findAllNonEmpty(orgId, format, accessKey)

    // when there are no collections throw "noSetHierarchy" ErrorResponse
    if (collections.size == 0) return createErrorResponse("noSetHierarchy")

    <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd">
      <responseDate>{ OaiPmhService.currentDate }</responseDate>
      <request verb="ListSets">{ requestURL }</request>
      <ListSets>
        {
          for (set <- collections) yield <set>
                                           <setSpec>{ set.spec }</setSpec>
                                           <setName>{ set.getName }</setName>
                                           <setDescription>
                                             <description>{ set.getDescription.getOrElse("") }</description>
                                             <totalRecords>{ set.getTotalRecords }</totalRecords>{
                                               if (set.isInstanceOf[OrganizationCollectionMetadata]) {
                                                 val organizationCollectionInformation = set.asInstanceOf[OrganizationCollectionMetadata]
                                                 <dataProvider>{ organizationCollectionInformation.getDataProvider }</dataProvider>
                                               }
                                             }
                                           </setDescription>
                                         </set>
        }
      </ListSets>
    </OAI-PMH>
  }

  /**
   * This method can give back the following Error and Exception conditions: idDoesNotExist, noMetadataFormats.
   */

  def processListMetadataFormats(pmhRequestEntry: PmhRequestEntry): Elem = {

    val identifier = pmhRequestEntry.pmhRequestItem.identifier
    val identifierSpec = identifier.split("_").head

    // if no identifier present list all formats
    // otherwise only list the formats available for the identifier
    val allMetadataFormats = if (identifier.isEmpty) {
      harvestCollectionLookupService.getAllMetadataFormats(orgId, accessKey)
    } else {
      harvestCollectionLookupService.findBySpecAndOrgId(identifierSpec, orgId).map {
        c => c.getVisibleMetadataSchemas(accessKey)
      }.getOrElse(List.empty)
    }

    // apply format filter
    val metadataFormats = if (format.isDefined) allMetadataFormats.filter(_.prefix == format.get) else allMetadataFormats

    def formatRequest: Elem = if (!identifier.isEmpty) <request verb="ListMetadataFormats" identifier={ identifier }>{ requestURL }</request>
    else <request verb="ListMetadataFormats">{ requestURL }</request>

    val elem =
      <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd">
        <responseDate>{ OaiPmhService.currentDate }</responseDate>
        { formatRequest }
        <ListMetadataFormats>
          {
            for (format <- metadataFormats) yield <metadataFormat>
                                                    <metadataPrefix>{ format.prefix }</metadataPrefix>
                                                    <schema>{ format.schema }</schema>
                                                    <metadataNamespace>{ format.namespace }</metadataNamespace>
                                                  </metadataFormat>
          }
        </ListMetadataFormats>
      </OAI-PMH>
    elem
  }

  def processListRecords(pmhRequestEntry: PmhRequestEntry, idsOnly: Boolean = false): Elem = {

    val setName = pmhRequestEntry.getSet
    if (setName.isEmpty) throw new BadArgumentException("No set provided")
    val metadataFormat = pmhRequestEntry.getMetadataFormat

    if (format.isDefined && metadataFormat != format.get) throw new MappingNotFoundException("Invalid format provided for this URL")

    val collection = harvestCollectionLookupService.findBySpecAndOrgId(setName, orgId).getOrElse {
      throw new DataSetNotFoundException("unable to find set: " + setName)
    }

    val schema: Option[RecordDefinition] = collection.getVisibleMetadataSchemas(accessKey).find(f => f.prefix == metadataFormat)
    if (!schema.isDefined) {
      throw new MappingNotFoundException("Format %s unknown".format(metadataFormat))
    }
    val (records, totalValidRecords) = collection.getRecords(metadataFormat, pmhRequestEntry.getLastTransferIdx, pmhRequestEntry.recordsReturned, pmhRequestEntry.pmhRequestItem.from, pmhRequestEntry.pmhRequestItem.until)

    val recordList = records.toList

    if (recordList.size == 0) throw new RecordNotFoundException(requestURL)

    val from = OaiPmhService.printDate(recordList.head.modified)
    val to = OaiPmhService.printDate(recordList.last.modified)

    val elem: Elem = if (!idsOnly) {
      <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd">
        <responseDate>{ OaiPmhService.currentDate }</responseDate>
        <request verb="ListRecords" from={ from } until={ to } metadataPrefix={ metadataFormat }>{ requestURL }</request>
        <ListRecords>
          {
            for (record <- recordList) yield renderRecord(record, metadataFormat, setName)
          }
          { pmhRequestEntry.renderResumptionToken(recordList, totalValidRecords) }
        </ListRecords>
      </OAI-PMH>
    } else {
      <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd">
        <responseDate>{ OaiPmhService.currentDate }</responseDate>
        <request verb="ListIdentifiers" from={ from } until={ to } metadataPrefix={ metadataFormat } set={ setName }>{ requestURL }</request>
        <ListIdentifiers>
          {
            for (record <- recordList) yield <header status={ recordStatus(record) }>
                                               <identifier>{ OaiPmhService.toPmhId(record.itemId) }</identifier>
                                               <datestamp>{ record.modified }</datestamp>
                                               <setSpec>{ setName }</setSpec>
                                             </header>
          }
          { pmhRequestEntry.renderResumptionToken(recordList, totalValidRecords) }
        </ListIdentifiers>
      </OAI-PMH>
    }

    prependNamespaces(metadataFormat, schema.get.schemaVersion, collection, elem)
  }

  def fromPmhIdToHubId(pmhId: String): String = {
    val pmhIdExtractor = """^oai:(.*?)_(.*?):(.*)$""".r
    val pmhIdExtractor(orgId, spec, localId) = pmhId
    "%s_%s_%s".format(orgId, spec, localId)
  }

  def processGetRecord(pmhRequestEntry: PmhRequestEntry): Elem = {
    val pmhRequest = pmhRequestEntry.pmhRequestItem
    // get identifier and format from map else throw BadArgument Error
    if (pmhRequest.identifier.isEmpty || pmhRequest.metadataPrefix.isEmpty) return createErrorResponse("badArgument")
    if (pmhRequest.identifier.split(":").length < 2) return createErrorResponse("idDoesNotExist")

    val identifier = fromPmhIdToHubId(pmhRequest.identifier)
    val metadataFormat = pmhRequest.metadataPrefix

    if (format.isDefined && metadataFormat != format.get) throw new MappingNotFoundException("Invalid format provided for this URL")

    val hubId = HubId(identifier)
    // check access rights
    val c = harvestCollectionLookupService.findBySpecAndOrgId(hubId.spec, orgId)
    if (c == None) return createErrorResponse("noRecordsMatch")
    if (!c.get.getVisibleMetadataSchemas(accessKey).exists(_.prefix == metadataFormat)) {
      return createErrorResponse("idDoesNotExist")
    }

    val record: MetadataItem = {
      val cache = MetadataCache.get(orgId, hubId.spec, c.get.itemType)
      val mdRecord = cache.findOne(identifier)
      if (mdRecord == None) return createErrorResponse("noRecordsMatch")
      else mdRecord.get
    }

    val collection = harvestCollectionLookupService.findBySpecAndOrgId(identifier.split("_")(1), identifier.split("_")(0)).get

    val elem: Elem =
      <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd">
        <responseDate>{ OaiPmhService.currentDate }</responseDate>
        <request verb="GetRecord" identifier={ OaiPmhService.toPmhId(record.itemId) } metadataPrefix={ metadataFormat }>{ requestURL }</request>
        <GetRecord>{ renderRecord(record, metadataFormat, identifier.split("_")(1)) }</GetRecord>
      </OAI-PMH>

    prependNamespaces(metadataFormat, record.schemaVersions.get(metadataFormat).getOrElse("1.0.0"), collection, elem)
  }

  // todo find a way to not show status namespace when not deleted
  private def recordStatus(record: MetadataItem): String = "" // todo what is the sense of deleted here? do we need to keep deleted references?

  private def renderRecord(record: MetadataItem, metadataPrefix: String, set: String): Elem = {

    val cachedString: String = record.xml.get(metadataPrefix).getOrElse(throw new RecordNotFoundException(OaiPmhService.toPmhId(record.itemId)))

    // cached records may contain the default namespace, however in our situation that one is already defined in the top scope
    val cleanString = cachedString.replaceFirst("xmlns=\"http://www.w3.org/2000/xmlns/\"", "")

    val response = try {
      val elem: Elem = XML.loadString(cleanString)
      <record>
        <header>
          <identifier>{ URLEncoder.encode(OaiPmhService.toPmhId(record.itemId), "utf-8").replaceAll("%3A", ":") }</identifier>
          <datestamp>{ OaiPmhService.printDate(record.modified) }</datestamp>
          <setSpec>{ set }</setSpec>
        </header>
        <metadata>
          { elem }
        </metadata>
      </record>
    } catch {
      case e: Throwable =>
        log.error("Unable to render record %s with format %s because of %s".format(OaiPmhService.toPmhId(record.itemId), metadataPrefix, e.getMessage), e)
        <record/>
    }
    response
  }

  private def prependNamespaces(metadataFormat: String, schemaVersion: String, collection: Harvestable, elem: Elem): Elem = {
    var mutableElem = elem

    val formatNamespaces = RecordDefinition.getRecordDefinition(metadataFormat, schemaVersion).get.allNamespaces
    val globalNamespaces = collection.getNamespaces.map(ns => Namespace(ns._1, ns._2, ""))
    val namespaces = (formatNamespaces ++ globalNamespaces).distinct.filterNot(_.prefix == "xsi")

    def collectNamespaces(ns: NamespaceBinding, namespaces: ArrayBuffer[(String, String)]): ArrayBuffer[(String, String)] = {
      if (ns == TopScope) {
        namespaces += (ns.prefix -> ns.uri)
      } else {
        namespaces += (ns.prefix -> ns.uri)
        collectNamespaces(ns.parent, namespaces)
      }
      namespaces
    }

    val existingNs = collectNamespaces(elem.scope, new ArrayBuffer[(String, String)])

    for (ns <- namespaces) {
      import xml.{ Null, UnprefixedAttribute }
      if (ns.prefix == null || ns.prefix.isEmpty) {
        if (!existingNs.exists(p => p._1 == null || p._1.isEmpty)) {
          mutableElem = mutableElem % new UnprefixedAttribute("xmlns", ns.uri, Null)
        }
      } else {
        if (!existingNs.exists(_._1 == ns.prefix)) {
          mutableElem = mutableElem % new UnprefixedAttribute("xmlns:" + ns.prefix, ns.uri, Null)
        }
      }
    }
    mutableElem
  }

  def createPmhRequest(params: Map[String, Seq[String]], verb: PmhVerb): PmhRequestEntry = {

    def getParam(key: String) = params.get(key).map(_.headOption.getOrElse("")).getOrElse("")

    def parseDate(date: String) = try {
      OaiPmhService.dateFormat.parse(date)
    } catch {
      case t: Throwable => {
        try {
          OaiPmhService.utcFormat.parse(date)
        } catch {
          case t: Throwable =>
            log.warn("Trying to parse invalid date " + date)
            new Date()
        }
      }
    }

    val pmh = PmhRequestItem(
      verb,
      getParam("set"),
      params.get("from").map(_.headOption.getOrElse("")).map(parseDate(_)),
      params.get("until").map(_.headOption.getOrElse("")).map(parseDate(_)),
      getParam("metadataPrefix"),
      getParam("identifier")
    )
    PmhRequestEntry(pmh, getParam("resumptionToken"))
  }

  /**
   * This method is used to create all the OAI-PMH error responses to a given OAI-PMH request. The error descriptions have
   * been taken directly from the specifications document for v.2.0.
   */
  def createErrorResponse(errorCode: String, exception: Exception): Elem = {
    log.error(errorCode, exception)
    createErrorResponse(errorCode)
  }

  def createErrorResponse(errorCode: String): Elem = {
    <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd">
      <responseDate>{ OaiPmhService.currentDate }</responseDate>
      <request>{ requestURL }</request>
      {
        errorCode match {
          case "badArgument" => <error code="badArgument">The request includes illegal arguments, is missing required arguments, includes a repeated argument, or values for arguments have an illegal syntax.</error>
          case "badResumptionToken" => <error code="badResumptionToken">The value of the resumptionToken argument is invalid or expired.</error>
          case "badVerb" => <error code="badVerb">Value of the verb argument is not a legal OAI-PMH verb, the verb argument is missing, or the verb argument is repeated.</error>
          case "cannotDisseminateFormat" => <error code="cannotDisseminateFormat">The metadata format identified by the value given for the metadataPrefix argument is not supported by the item or by the repository.</error>
          case "idDoesNotExist" => <error code="idDoesNotExist">The value of the identifier argument is unknown or illegal in this repository.</error>
          case "noMetadataFormats" => <error code="noMetadataFormats">There are no metadata formats available for the specified item.</error>
          case "noRecordsMatch" => <error code="noRecordsMatch">The combination of the values of the from, until, set and metadataPrefix arguments results in an empty list.</error>
          case "noSetHierarchy" => <error code="noSetHierarchy">This repository does not support sets or no sets are publicly available for this repository.</error> // Should never be used. We only use sets
          case _ => <error code="unknown">Unknown Error Corde</error> // should never happen.
        }
      }
    </OAI-PMH>
  }

  case class PmhRequestItem(verb: PmhVerb, set: String, from: Option[Date], until: Option[Date], metadataPrefix: String, identifier: String)

  case class PmhRequestEntry(pmhRequestItem: PmhRequestItem, resumptionToken: String) {

    val recordsReturned = configuration.oaiPmhService.responseListSize

    private val ResumptionTokenExtractor = """(.+?):(.+?):(.+?):(.+?):(.+)""".r

    lazy val ResumptionTokenExtractor(set, metadataFormat, recordInt, pageNumber, originalSize) = resumptionToken // set:medataFormat:lastTransferIdx:numberSeen

    // MUSIT:ese:250:1:70041/250
    def getSet = if (resumptionToken.isEmpty) pmhRequestItem.set else set
    def getMetadataFormat = if (resumptionToken.isEmpty) pmhRequestItem.metadataPrefix else metadataFormat
    def getPagenumber = if (resumptionToken.isEmpty) 1 else pageNumber.toInt
    def getLastTransferIdx = if (resumptionToken.isEmpty) 0 else recordInt.toInt
    def getOriginalListSize = if (resumptionToken.isEmpty) 0 else originalSize.toInt

    def renderResumptionToken(recordList: List[MetadataItem], totalListSize: Long) = {

      val originalListSize = if (getOriginalListSize == 0) totalListSize else getOriginalListSize

      val currentPageNr = if (resumptionToken.isEmpty) getPagenumber else getPagenumber + 1

      val nextIndex = recordList.last.index // currentPageNr * recordsReturned

      val nextResumptionToken = "%s:%s:%s:%s:%s".format(getSet, getMetadataFormat, nextIndex, currentPageNr, originalListSize)

      val cursor = currentPageNr * recordsReturned
      if (cursor < originalListSize) {
        <resumptionToken expirationDate={ OaiPmhService.printDate(new Date()) } completeListSize={ (originalListSize).toString } cursor={ cursor.toString }>{ nextResumptionToken }</resumptionToken>
      } else
        <resumptionToken/>
    }

  }

}

object OaiPmhService {

  val utcFormat: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
  val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
  utcFormat.setLenient(false)
  dateFormat.setLenient(false)

  def toUtcDateTime(date: Date): String = utcFormat.format(date)
  def currentDate = toUtcDateTime(new Date())
  def printDate(date: Date): String = if (date != null) toUtcDateTime(date) else ""

  /**
   * Turns a hubId (orgId_spec_localId) into a pmhId of the kind oai:kulturnett_kulturit.no:NOMF-00455Q
   */
  def toPmhId(hubId: String) = HubId(hubId).pmhId

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