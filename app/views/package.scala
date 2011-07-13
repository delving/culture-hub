package views {

import play.data.validation.Validation

package object context {

  def flash = play.mvc.Scope.Flash.current()
  def params = play.mvc.Scope.Params.current()
  def renderArgs = play.mvc.Scope.RenderArgs.current()
  def validation = Validation.current()
  def errors = validation.errorsMap()
  def showError(key: String) = Validation.error(key)


}

}
