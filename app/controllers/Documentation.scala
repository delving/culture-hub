package controllers

import java.io.File
import play.Play
import org.pegdown.PegDownProcessor
import play.libs.IO
import play.mvc.results.RenderBinary
import play.mvc.{Util, Controller}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Documentation extends Controller {

  def helpIndex = page("home", null, "help")
  def developerIndex = page("home", null, "developer")

  def help(id: String, category: String) = page(id, category, "help")
  def developer(id: String, category: String) = page(id, category, "developer")

  @Util def page(id: String, category: String, docType: String): AnyRef = {
    val cat = if(category == null) "" else category + "/"
    val page: File = new File(Play.applicationPath, "documentation/%s/%s%s.markdown".format(docType, cat, id))
    if (!page.exists()) return NotFound("Could not find page '%s'".format(cat + id))

    val markup = IO.readContentAsString(page)
    val converted = toHTML(markup)
    val title = getTitle(markup)

    Template("/Documentation/page.html", 'title -> title, 'html -> converted)
  }

  def image(name: String): AnyRef = {
    val image: File = new File(Play.applicationPath, "documentation/images/" + name + ".png")
    if(!image.exists()) return NotFound
    new RenderBinary(image)
  }

  private def getTitle(markup: String): String = if (markup.length == 0) "" else markup.split("\n")(0).substring(2).trim

  private def toHTML(markup: String): String = new PegDownProcessor().markdownToHtml(markup)
}