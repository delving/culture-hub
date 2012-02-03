package controllers

import core.ThemeAware
import play.api.mvc.Controller
import play.templates.groovy.GroovyTemplates

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DelvingController {

}

trait ApplicationController extends Controller with GroovyTemplates with ThemeAware with Logging