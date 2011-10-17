package controllers.user

import controllers.DelvingController
import play.mvc.results.Result

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
object Admin extends DelvingController with UserSecured {

  def index: Result = Template

}