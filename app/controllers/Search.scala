package controllers

import play.api.mvc._
import models._
import core.Constants._
import core.search._
import exceptions._
import play.api.i18n.Messages
import core.rendering.ViewRenderer
import com.mongodb.casbah.Imports._
import play.api.Play
import util.DomainConfigurationHandler

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Search extends DelvingController {
  
  // TODO move later
  lazy val viewRenderers: Map[DomainConfiguration, Map[String, ViewRenderer]] = RecordDefinition.enabledDefinitions.map {
    pair => {
      (pair._1 -> {
        pair._2.
          flatMap(f => ViewRenderer.fromDefinition(f, "html", pair._1)).
          map(r => (r.schema -> r)).toMap[String, ViewRenderer]
      })
    }
  }

  def index(query: String, page: Int) = search(query, page)

  def search(query: String = "*:*", page: Int = 1, additionalSystemHQFs: List[String] = List.empty[String]) = Root {
    Action {
      implicit request =>
        val chQuery = SolrQueryService.createCHQuery(request, configuration, true, Option(connectedUser), additionalSystemHQFs)
        try {
          val response = CHResponse(Params(request.queryString), configuration, SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, configuration, true), chQuery)
          val briefItemView = BriefItemView(response)

          Ok(Template("/Search/index.html",
            'briefDocs -> briefItemView.getBriefDocs,
            'pagination -> briefItemView.getPagination,
            'facets -> briefItemView.getFacetQueryLinks,
            'themeFacets -> configuration.getFacets,
            'searchTerm -> query,
            'returnToResults -> request.rawQueryString)).withSession(
            session +
              (RETURN_TO_RESULTS -> request.rawQueryString) +
              (SEARCH_TERM -> query))
        } catch {
          case MalformedQueryException(s, t) => BadRequest(Template("/Search/invalidQuery.html", 'query -> query))
          case c: SolrConnectionException => Error(Messages("search.backendConnectionError"))
        }
    }
  }

  def record(orgId: String, spec: String, recordId: String, overlay: Boolean = false) = Root {
    Action {
      implicit request =>
        DataSet.dao.findBySpecAndOrgId(spec, orgId).map {
          collection =>
            val hubId = "%s_%s_%s".format(orgId, spec, recordId)

            MetadataCache.get(orgId, spec, ITEM_TYPE_MDR).findOne(hubId) match {
              case Some(mdr) =>

                val facts = collection.details.facts.asDBObject.map(kv => (kv._1.toString -> kv._2.toString))

                // TODO this is a workaround for not yet having a resolver for directory entries
                if(facts.contains("providerUri")) {
                  facts.put("resolvedProviderUri", "/%s/museum/%s".format(orgId, facts("providerUri").split("/").reverse.head))
                }
                if(facts.contains("dataProviderUri")) {
                  facts.put("resolvedDataProviderUri", "/%s/museum/%s".format(orgId, facts("dataProviderUri").split("/").reverse.head))
                }

                // AFF takes precedence over anything else
                if(mdr.xml.get("aff").isDefined) {
                  val record = mdr.xml.get("aff").get
                  renderRecord(mdr, record, viewRenderers(configuration)("aff"), RecordDefinition.getRecordDefinition("aff").get, orgId, facts.toMap)
                } else {
                  val ds = DataSet.dao.findBySpecAndOrgId(spec, orgId)
                  if(ds.isDefined) {
                    // use the indexing format as rendering format. if none is set try to find the first suitable one
                    val inferredRenderingFormat = mdr.xml.keys.toList.intersect(RecordDefinition.enabledDefinitions(configuration).toList).headOption
                    val renderingFormat = ds.get.idxMappings.headOption.orElse(inferredRenderingFormat)
                    if(renderingFormat.isDefined && viewRenderers(configuration).contains(renderingFormat.get) && mdr.xml.contains(renderingFormat.get)) {
                      val record = mdr.xml.get(renderingFormat.get).get
                      renderRecord(mdr, record, viewRenderers(configuration)(renderingFormat.get), RecordDefinition.getRecordDefinition(renderingFormat.get).get, orgId, facts.toMap)
                    } else {
                      NotFound(Messages("heritageObject.notViewable"))
                    }
                  } else {
                    NotFound(Messages("datasets.dataSetNotFound", spec))
                  }
                }

              case None => NotFound("Record was not found")
            }
        }.getOrElse {
          NotFound("Collection was not found")
        }
    }
  }

  private def renderRecord(mdr: MetadataItem, record: String, viewRenderer: ViewRenderer, definition: RecordDefinition, orgId: String, parameters: Map[String, String] = Map.empty)(implicit request: RequestHeader) = {

    val renderResult = viewRenderer.renderRecord(record, getUserGrantTypes(orgId), definition.getNamespaces, lang, parameters)

    val updatedSession = if (request.headers.get(REFERER) == None || !request.headers.get(REFERER).get.contains("search")) {
      // we're coming from someplace else then a search, remove the return to results cookie
      request.session - (RETURN_TO_RESULTS)
    } else {
      request.session
    }

    val returnToResults = updatedSession.get(RETURN_TO_RESULTS).getOrElse("")
    val searchTerm = updatedSession.get(SEARCH_TERM).getOrElse("")

    Ok(Template("Search/object.html", 'systemFields -> mdr.systemFields, 'fullView -> renderResult.toViewTree, 'returnToResults -> returnToResults, 'searchTerm -> searchTerm)).withSession(updatedSession)

  }


  // ~~~ Utility methods (not controller actions)

  def search(user: Option[String], page: Int, configuration: DomainConfiguration, query: List[String])(implicit request: RequestHeader) = {
    val start = (page - 1) * PAGE_SIZE + 1
    val queryList = (user match {
      case Some(u) => List("%s:%s".format(OWNER, u))
      case None => List()
    }) ::: query
    val chQuery = SolrQueryService.createCHQuery(request, configuration, false, Option(connectedUser), queryList)
    val queryResponse = SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, configuration, true)
    val chResponse = CHResponse(Params(request.queryString + ("start" -> Seq(start.toString))), configuration, queryResponse, chQuery)
    val briefItemView = BriefItemView(chResponse)

    val items = briefItemView.getBriefDocs.map(bd =>
      ListItem(id = bd.getHubId,
        itemType = bd.getItemType,
        title = bd.getTitle,
        description = bd.getDescription,
        thumbnailUrl = Some(bd.getThumbnailUri(220, configuration)),
        userName = bd.getOrgId,
        isPrivate = bd.getVisibility.toInt == Visibility.PRIVATE.value,
        url = bd.getUri(configuration),
        mimeType = bd.getMimeType))

    (items, briefItemView.pagination.getNumFound)
  }


}
