package controllers

import play.mvc.Controller
import java.io.File
import play.Play
import org.pegdown.PegDownProcessor
import play.libs.IO
import play.mvc.results.RenderBinary

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Documentation extends Controller {

  import views.Documentation._

  def index = page("home", null)

  def page(id: String, category: String): AnyRef = {
    val cat = if(category == null) "" else category + "/"
    val page: File = new File(Play.applicationPath, "documentation/help/" + cat + id + ".markdown")
    if (!page.exists()) return NotFound("Could not find page '%s'".format(cat + id))

    val markup = IO.readContentAsString(page)
    val converted = toHTML(markup)
    val title = getTitle(markup)

    html.page(title = title, html = converted)
  }

  def image(name: String): AnyRef = {
    val image: File = new File(Play.applicationPath, "documentation/images/" + name + ".png")
    if(!image.exists()) return NotFound
    new RenderBinary(image)
  }

  private def getTitle(markup: String): String = if (markup.length == 0) "" else markup.split("\n")(0).substring(2).trim

  private def toHTML(markup: String): String = new PegDownProcessor().markdownToHtml(markup)
}