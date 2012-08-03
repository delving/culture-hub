package controllers.search

import play.api.mvc._
import controllers.{CommonSearch, DelvingController}
import scala.collection.JavaConverters._
import models.{DomainConfiguration, DataSet}
import core.Constants._
import core.search.MalformedQueryException
import exceptions.SolrConnectionException
import play.api.i18n.Messages

/**
 * Advanced search. This one is tailored from ICN and is taken from the legacy system.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object AdvancedSearch extends DelvingController {

  def advancedSearch = Root {
    Action {
      implicit request =>
        Ok(Template('orgId -> configuration.orgId))
    }
  }

  def submitAdvancedSearch = Root {
    Action(parse.urlFormEncoded) {
      implicit request =>

        def field(name: String) = request.body.getFirst(name).getOrElse(null)

        // TODO this is taken straight from the legacy code-base, and should be replaced by something more elegant
        val form = new AdvancedSearchForm()
        form.setFacet0(field("facet0"))
        form.setValue0(field("value0"))
        form.setOperator1(field("operator1"))
        form.setFacet1(field("facet1"))
        form.setValue1(field("value1"))
        form.setOperator2(field("operator2"))
        form.setFacet2(field("facet2"))
        form.setValue2(field("value2"))
        form.setAllCollections(field("allCollections"))
        form.setCollectionList(request.body.get("collectionList").getOrElse(Seq.empty).toArray)

        val query = form.toSolrQuery

        Ok("/search?query=" + query)
    }

  }

}
