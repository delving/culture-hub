package controllers

import play.api.mvc._
import play.templates.groovy.GroovyTemplates

object Application extends Controller with GroovyTemplates with Secured {

  def index = IsAuthenticated { username => implicit request => Ok(Template) }

}