package controllers

import com.mongodb.casbah.Imports._
import org.apache.log4j.Logger
import java.util.Date
import java.awt.image.BufferedImage
import com.thebuzzmedia.imgscalr.Scalr
import javax.imageio.ImageIO
import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import com.mongodb.casbah.gridfs.{GridFSInputFile, GridFSDBFile, GridFS}
import play.mvc.Http.Response
import play.mvc.results.{NotFound, RenderBinary, Result}
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.httpclient.Header
import play.Play
import play.utils.Utils

/**
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @since 1/2/11 10:09 PM
 */
object Images extends DelvingController {

  def view(image: String): AnyRef = {

    // just for testing
    //val smallballs = getPath("image.store.path") + "/" + image + ".tif"
    val smallballs = Play.applicationPath.getAbsolutePath + "/public/images/smallballs.tif"

    if (image.isEmpty || image.equalsIgnoreCase("smallballs")) Template('image -> smallballs)
    else Template("/Image/view.html", 'image -> (Play.configuration.getProperty("image.store.path") + "/" + image))

  }

  def iipsrv(): Result = {
    // TODO this should be a permanent redirect
    Redirect(Play.configuration.getProperty("iipsrv.url") + "?" + request.querystring, true)
  }

}