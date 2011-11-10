package controllers.custom

import play.mvc.results.RenderXml
import play.mvc.Controller
import controllers.ThemeAware
import scala.collection.JavaConversions.asScalaIterable

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 11/5/11 10:35 AM  
 */

object ItinEndPoint extends Controller with ThemeAware{

  import play.mvc.results.Result

  def search: Result = {
    import controllers.search.SearchService
    SearchService.getApiResult(request, theme)
  }

  def store(data: String): Result = {
    import play.data.Upload
    import java.util.List
    import models.{StoreResponse, DrupalEntity}
    import play.Logger
    import java.lang.String
    import xml.Elem

    val uploads: List[Upload] = request.args.get("__UPLOADS").asInstanceOf[java.util.List[play.data.Upload]]
    val body: String = params.get("body")
    val uploadedXml: Option[Elem] =
      if (uploads != null) Some(xml.XML.load(asScalaIterable(uploads).head.asStream()))
      else if (body != null && !body.isEmpty) Some(xml.XML.loadString(body))
      else None

    val xmlResponse = try {
      uploadedXml match {
        case x: Some[Elem] =>
          val response: StoreResponse = DrupalEntity.processStoreRequest(uploadedXml.get)((item, list) => DrupalEntity.insertInMongoAndIndex(item, list))
          StoreResponse(response.itemsParsed, response.coRefsParsed)
        case _ =>
          StoreResponse(success = false, errorMessage = "Unable to receive the file from the POST")
      }
    }
    catch {
      case ex: Exception =>
        Logger.error(ex, "Problem with the posted xml file")
        StoreResponse(success = false, errorMessage = "Unable to receive the xml-file from the POST")
    }

    val responseString =
    <response recordsProcessed={xmlResponse.itemsParsed.toString} linksProcessed={xmlResponse.coRefsParsed.toString}>
      <status>{if (xmlResponse.success) "succcess" else "failure"}</status>
      {if (!xmlResponse.errorMessage.isEmpty) <error> {xmlResponse.errorMessage}</error>}
    </response>
    if (!xmlResponse.success) response.status = new Integer(404)
    Xml(responseString.toString())
  }

  // todo implement this
  def sync(data: String): Result = {
    new RenderXml("<bla>rocks</bla>")
  }

}