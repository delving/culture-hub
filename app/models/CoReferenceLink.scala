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

case class FromLink (uri: String, extra: Map[String,  String[Array]], crmClass: String, linkType: String)
case class ToLink(uri: String,  extra: Map[String,  String[Array]], crmClass: String, linkType: String)

case class LinkCreation(creationDate: Date)
case class LinkOrigin(context: String,  siteUri: String, siteType: String)

case class LinkDescription(linkType: String,  value: String,  note: String,  quality: String)

// todo remove later
case class CoRefInfo(comments: String, relation: String, relationQuality: String)
case class LinkInfo(linkType: String, value: String,  note: String,  linkContext: String)

