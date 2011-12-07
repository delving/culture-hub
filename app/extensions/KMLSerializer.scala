/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package extensions

import collection.JavaConversions._
import de.micromata.opengis.kml.v_2_2_0._
import java.io.StringWriter
import models.User
import scala.Predef._
import xml.{XML, Node}

object KMLSerializer {

  def toKml(user: Option[User]): Node = {

    // just an example

    val kml: Kml = new Kml
    val document: Document = new Document
    kml.setFeature(document)

    user foreach {
      user => {
        document.setName(user.fullname.replaceAll(" ", "_") + ".kml");
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
        placemark.setName(user.fullname);
        placemark.setDescription("This is the user " + user.fullname);
        placemark.setStyleUrl("#exampleBalloonStyle");

        val point: Point = new Point;
        placemark.setGeometry(point);
        val coord: List[Coordinate] = List(new Coordinate(-122.370533, 37.823842, 0))
        point.setCoordinates(coord);
      }
    }

    val writer: StringWriter = new StringWriter
    kml.marshal(writer)

    XML.loadString(writer.toString)
  }
}