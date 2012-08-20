package controllers.search

import play.api.mvc._
import play.api.Play.current
import controllers.DelvingController
import play.api.Play
import core.CultureHubPlugin
import models.DomainConfiguration
import plugins.AdvancedSearchPlugin

/**
 * Advanced search. This one is tailored from ICN and is taken from the legacy system.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object AdvancedSearch extends DelvingController {

  def advancedSearch = Root {
    Action {
      implicit request =>

        advancedSearchType.map { searchForm => searchForm match {
          case "icn" =>
            Ok(Template("/search/AdvancedSearch/advancedSearch1.html", 'orgId -> configuration.orgId))
          case "kulturnett" =>
            Ok(Template("/search/AdvancedSearch/advancedSearch2.html", 'orgId -> configuration.orgId))
        }

        }.getOrElse {
          NotFound("Advanced search not configured")
        }
    }
  }

  def submitAdvancedSearch = Root {
    Action(parse.urlFormEncoded) {
      implicit request =>

        def field(name: String) = request.body.getFirst(name).getOrElse(null)

        advancedSearchType.map {
          searchForm =>

            val query = searchForm match {

              case "icn" =>
                val form = new AdvancedSearchForm1()
                form.setFacet0(field("facet0"))
                form.setValue0(field("value0"))
                form.setOperator1(field("operator1"))
                form.setFacet1(field("facet1"))
                form.setValue1(field("value1"))
                form.setOperator2(field("operator2"))
                form.setFacet2(field("facet2"))
                form.setValue2(field("value2"))
                form.setAllOwners(field("allOwners"))
                form.setOwnerList(request.body.get("ownersList").getOrElse(Seq.empty).toArray)

                form.toSolrQuery

              case "kulturnett" =>
                val form = new AdvancedSearchForm2()
                form.setFacet0(field("facet0"))
                form.setValue0(field("value0"))
                form.setOperator1(field("operator1"))
                form.setFacet1(field("facet1"))
                form.setValue1(field("value1"))
                form.setOperator2(field("operator2"))
                form.setFacet2(field("facet2"))
                form.setValue2(field("value2"))
                form.setAllOwners(field("allOwners") == "all")
                form.setAllCounties(field("allCounties") == "all")
                form.setAllMunicipalities(field("allMunicipalities") == "all")
                form.setOwnerList(request.body.get("ownersList").getOrElse(Seq.empty).toArray)
                form.setCountyList(request.body.get("countyList").getOrElse(Seq.empty).toArray)
                form.setMunicipalityList(request.body.get("municipalityList").getOrElse(Seq.empty).toArray)
                form.setOnlyDigitalObjects(field("onlyDigitalObjects") == "true")
                form.setAllTypes(field("allTypes") == "all")
                form.setTypeList(request.body.get("typeList").getOrElse(Seq.empty).toArray)
                form.setCreationFrom(field("creationFrom"))
                form.setCreationTo(field("creationTo"))

                form.toSolrQuery


            }

            Ok("/search?query=" + query)


        }.getOrElse {
          NotFound("Advanced search not configured")
        }
    }

  }

  private def advancedSearchType(implicit configuration: DomainConfiguration): Option[String] = CultureHubPlugin.getEnabledPlugins.find(_.pluginKey == "advanced-search").flatMap { p =>
    p.asInstanceOf[AdvancedSearchPlugin].getAdvancedSearchType
  }

}
