package controllers.custom

import play.mvc.results.RenderXml
import play.mvc.Controller
import controllers.DelvingController

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 11/5/11 10:35 AM  
 */

object ItinEndPoint extends DelvingController {

  import play.mvc.results.Result

  def search: Result = {
    import controllers.search.SearchService
    SearchService.getApiResult(request, theme)
  }

  def store(data: String): Result = {
    import play.data.Upload
    import java.util.List
    import models.{StoreResponse, DrupalEntity}
    val uploads: List[Upload] = request.args.get("__UPLOADS").asInstanceOf[java.util.List[play.data.Upload]]
    val response = scala.collection.JavaConversions.asScalaIterable(uploads).foldLeft(StoreResponse()){
      (storeResponse, upload) => {
        upload.getContentType match {
          case "application/xml" => {
            val data = xml.XML.load(upload.asStream())
            val response: StoreResponse = DrupalEntity.processStoreRequest(data)((item, list) => DrupalEntity.insertInMongoAndIndex(item, list))
            storeResponse.copy(
              itemsParsed = storeResponse.itemsParsed + response.itemsParsed,
              coRefsParsed = storeResponse.coRefsParsed + response.coRefsParsed
            )
          }
          case _ => storeResponse
        }
      }
    }

    val responseString =
    <response recordsProcessed={response.itemsParsed.toString} linksProcessed={response.coRefsParsed.toString}>
      <status>{if (response.success) "succcess" else "failure"}</status>
      {if (!response.errorMessage.isEmpty) <error> {response.errorMessage}</error>}
    </response>
    new RenderXml(responseString.toString())
  }

  // todo implement this
  def sync(data: String): Result = {

    new RenderXml("<bla>rocks</bla>")
  }

}