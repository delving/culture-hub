package controllers

/**
 * @author Eric van der Meulen <eric@delving.eu>
 */

object Search extends DelvingController {

  import views.Search._

  def index() = {
    html.index()
  }

}