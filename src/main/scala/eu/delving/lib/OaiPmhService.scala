package eu.delving.lib

import net.liftweb.http.rest.RestHelper
import net.liftweb.common.Logger
import net.liftweb.http.XmlResponse._
import xml.Elem
import org.joda.time.DateTime
import net.liftweb.http._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object OaiPmhService extends RestHelper {

  val log = Logger("OaiPmhService")

  private val VERB = "verb"
  private val legalParameterKeys = List("verb", "identifier", "metadataPrefix", "set", "from", "until", "resumptionToken", "accessKey")

  def isGetOrPost(r: Req): Boolean = {
    (r.requestType.get_? || r.requestType.post_?)
  }

  protected trait LegalPmhRequestTest {
    def testResponse_?(r: Req): Boolean = {
      log.info("Testing for valid request")
      r.param(VERB).isDefined && hasLegalPmhParameters(r.params) && isGetOrPost(r)
    }
  }

  /**test for a Pmh request attempt, since we need to return an error **/
  protected trait IllegalPmhRequestTest {
    def testResponse_?(r: Req): Boolean = {
      log.info("Testing for invalid request")
      r.param(VERB).isDefined && !hasLegalPmhParameters(r.params) && isGetOrPost(r)
    }
  }

  def hasLegalPmhParameters(params: Map[String, List[String]]): Boolean = {
    // no repeated queryParameters are allowed
    if (params.values.exists(value => value.length > 1)) return false

    // check for illegal queryParameter keys
    if (!(params.keys filterNot (legalParameterKeys contains)).isEmpty) return false

    true
  }

  protected lazy val LegalPmhRequest = new TestReq with LegalPmhRequestTest
  protected lazy val illegalPmhRequest = new TestReq with IllegalPmhRequestTest


  serve {
    case request @ "service" :: _ :: Nil LegalPmhRequest _ => {
      XmlResponse(<foo></foo>)
    }
    case request @ "service" :: _ :: Nil `illegalPmhRequest` _ => {
      XmlResponse(createErrorResponse("badArgument", request.uri))
    }
  }


  private def toUtcDateTime(date: DateTime): String = date.toString("yyyy-MM-dd'T'HH:mm:ss'Z'")

  private def currentDate = toUtcDateTime(new DateTime())


  def createErrorResponse(errorCode: String, url: String): Elem = {
    <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd">
      <responseDate>
        {currentDate}
      </responseDate>
      <request>
        {url}
      </request>{errorCode match {
      case "badArgument" => <error code="badArgument">The request includes illegal arguments, is missing required arguments, includes a repeated argument, or values for arguments have an illegal syntax.</error>
      case "badResumptionToken" => <error code="badResumptionToken">The value of the resumptionToken argument is invalid or expired.</error>
      case "badVerb" => <error code="badVerb">Value of the verb argument is not a legal OAI-PMH verb, the verb argument is missing, or the verb argument is repeated.</error>
      case "cannotDisseminateFormat" => <error code="cannotDisseminateFormat">The metadata format identified by the value given for the metadataPrefix argument is not supported by the item or by the repository.</error>
      case "idDoesNotExist" => <error code="idDoesNotExist">The value of the identifier argument is unknown or illegal in this repository.</error>
      case "noMetadataFormats" => <error code="noMetadataFormats">There are no metadata formats available for the specified item.</error>
      case "noRecordsMatch" => <error code="noRecordsMatch">The combination of the values of the from, until, set and metadataPrefix arguments results in an empty list.</error>
      case "noSetHierarchy" => <error code="noSetHierarchy">This repository does not support sets</error> // Should never be used. We only use sets
      case _ => <error code="unknown">Unknown Error Corde</error> // should never happen.
    }}
    </OAI-PMH>
  }


}