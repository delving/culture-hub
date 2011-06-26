package eu.delving.lib

import eu.delving.model.User
import de.micromata.opengis.kml.v_2_2_0._
import scala.collection.JavaConversions._
import java.io.StringWriter
import xml.{XML, Node}

/**
 * Concept class, serializes a model object to KML.
 * This has to happen on a per-model basis since the KML representation of a model can't be inferred from its data.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object KMLSerializer {


  def toKml(user: User): Node = {

    // just an example

    val kml: Kml = new Kml
    val document: Document = new Document
    kml.setFeature(document)

    document.setName(user.firstName + "_" + user.lastName + ".kml");
    document.setOpen(true);

    val style: Style = new Style
    document.getStyleSelector.add(style);
    style.setId("exampleBalloonStyle");

    val balloonstyle: BalloonStyle = new BalloonStyle
    style.setBalloonStyle(balloonstyle);
    balloonstyle.setId("ID");
    balloonstyle.setBgColor("ffffffbb");
    balloonstyle.setTextColor("ff000000");
    balloonstyle
            .setText("<![CDATA[" + "<b><font color='#CC0000' size='+3'>$[name]</font></b>" + "<br/><br/>" + "<font face='Courier'>$[description]</font>" + "<br/><br/>" + "Extra text that will appear in the description balloon" + "<br/><br/>" + "<!-- insert the to/from hyperlinks -->" + "$[geDirections]]]>");

    val placemark: Placemark = new Placemark
    document.getFeature.add(placemark);
    placemark.setName(user.fullName);
    placemark.setDescription("This is the user " + user.fullName);
    placemark.setStyleUrl("#exampleBalloonStyle");

    val point: Point = new Point;
    placemark.setGeometry(point);
    val coord: List[Coordinate] = List(new Coordinate(-122.370533, 37.823842, 0))
    point.setCoordinates(coord);

    val writer: StringWriter = new StringWriter
    kml.marshal(writer)

    XML.loadString(writer.toString)
  }


}