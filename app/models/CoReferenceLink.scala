package models

import org.bson.types.ObjectId
import java.util.Date

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 11/5/11 11:03 AM  
 */

case class CoReferenceLink(_id: ObjectId = new ObjectId,
                           link_uri: String, // uri
                           link_category: String, // maybe controlled list
                           who: LinkAuthor,
                           from: FromLink,
                           to: ToLink,
                           when: LinkCreation,
                           where: LinkOrigin,
                           why: LinkDescription
                           )
{
  def toXml: String = {
    "<link>implement the rest</link>"
  }

//  def fromXml(xmlString: String): CoReferenceLink = {
//    CoReferenceLink()
//  }

}


case class LinkAuthor(userUri: String,  name: String,  organisation: String, role: String)

case class FromLink (uri: String, linkType: String, title: String, crmClass: String = "", extra: Map[String, Array[String]] = Map.empty)
case class ToLink(uri: String, linkType: String, title: String, crmClass: String = "", extra: Map[String, Array[String]] = Map.empty)

case class LinkCreation(creationDate: Date)
case class LinkOrigin(context: String,  siteUri: String, siteType: String)

case class LinkDescription(linkType: String,  value: String,  note: String,  quality: String)