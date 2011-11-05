package custom

import play.test.UnitSpec
import org.scalatest.matchers.ShouldMatchers
import org.bson.types.ObjectId

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 11/5/11 2:31 PM  
 */

class ItinEndPointSpec extends UnitSpec with ShouldMatchers {

  describe("A ItinStoreRequestParser") {

      describe("(when receiving a request)") {

        it("should reject it when the size of records exceeds 1000")(pending)

        it("should count the numbers of records returned") {
          import models.DrupalEntity
          val response = DrupalEntity.processStoreRequest(sampleRecordAsString)((item, list) => println(item.toSolrDocument, list))
          response.itemsParsed should equal (2)
          response.coRefsParsed should not equal (0)
        }
      }
    }

  val sampleRecord =
    <record id="24" entitytype="node" bundle="history_event">
      <drup:nodetype type="string">history_event</drup:nodetype>
      <drup:created type="date">2011-08-10T13:44:35Z</drup:created>
      <drup:changed type="date">2011-09-19T13:54:19Z</drup:changed>
      <drup:title type="text">80-jarige oorlog</drup:title>
      <drup:pathalias type="string">gebeurtenissen/80-jarige-oorlog</drup:pathalias>
      <drup:path type="string">node/24</drup:path>
      <drup:uri type="string">http://itin.dev/gebeurtenissen/80-jarige-oorlog</drup:uri>
      <drup:author_id type="int">1</drup:author_id>
      <drup:author_name type="text">cinnamon</drup:author_name>
      <drup:body type="text"></drup:body>
      <itin:sections type="string" entitytype="taxonomy_term" bundle="sections" id="28">oorlog</itin:sections>
      <itin:sections type="string" entitytype="taxonomy_term" bundle="sections" id="30">geloof</itin:sections>
      <itin:sections type="string" entitytype="taxonomy_term" bundle="sections" id="31">politiek</itin:sections>
      <itin:related_pvb type="link" entitytype="node" bundle="pvb" id="29" linkType="internal" pathalias="plaatsen/kasteel-wittem" path="node/29">Kasteel Wittem</itin:related_pvb>
      <itin:related_pvb type="link" entitytype="node" bundle="pvb" id="53" linkType="internal" pathalias="plaatsen/ruinekerk-bergen-nh" path="node/53">Ruïnekerk (Bergen - NH)</itin:related_pvb>
      <itin:related_pvb type="link" entitytype="node" bundle="pvb" id="209" linkType="internal" pathalias="plaatsen/slot-loevestein" path="node/209">Slot Loevestein</itin:related_pvb>
      <itin:related_pvb type="link" entitytype="node" bundle="pvb" id="244" linkType="internal" pathalias="plaatsen/bastion-baselaar-s-hertogenbosch" path="node/244">Bastion Baselaar ('s-Hertogenbosch)</itin:related_pvb>
      <itin:related_pvb type="link" entitytype="node" bundle="pvb" id="261" linkType="internal" pathalias="plaatsen/vesting-naarden" path="node/261">Vesting Naarden</itin:related_pvb>
      <itin:related_pvb type="link" entitytype="node" bundle="pvb" id="292" linkType="internal" pathalias="plaatsen/pater-vinck-toren" path="node/292">Pater Vinck Toren</itin:related_pvb>
      <itin:history_date_start type="date">1568-05-12T00:00:00Z</itin:history_date_start>
      <itin:history_date_end type="date">1648-04-01T00:00:00Z</itin:history_date_end>
      <itin:ref_period type="link" entitytype="node" bundle="history_period" id="56" linkType="internal" pathalias="perioden/gouden-eeuw" path="node/56">Gouden Eeuw</itin:ref_period>
      <itin:ref_persons type="link" entitytype="node" bundle="history_person" id="30" linkType="internal" pathalias="personen/willem-van-oranje" path="node/30">Willem van Oranje</itin:ref_persons>
      <itin:date_start type="date">1970-01-01T00:00:00Z</itin:date_start>
      <itin:date_year_start type="date">1970-01-01T00:00:00Z</itin:date_year_start>
    </record>

    val sampleRecordAsString =
      """
    <record-list xmlns:drup="http://www.itin.nl/drupal" xmlns:itin="http://www.itin.nl/namespace" >
    <record id="1" entitytype="node" bundle="page">
      <drup:nodetype type="string">page</drup:nodetype>
      <drup:created type="date">2011-05-31T14:47:50Z</drup:created>
      <drup:changed type="date">2011-08-08T09:13:35Z</drup:changed>
      <drup:title type="text">Dit is de homepage</drup:title>
      <drup:pathalias type="string">home</drup:pathalias>
      <drup:path type="string">node/1</drup:path>
      <drup:uri type="string">http://itin.dev/home</drup:uri>
      <drup:author_id type="int">1</drup:author_id>
      <drup:author_name type="text">cinnamon</drup:author_name>
      <drup:body type="text">&lt;p&gt;Plaatsen van Betekenis is een erfgoed-Startup voor cultuurtoeristen, die najaar 2011 life gaat met een eerste release. We willen graag met JOU aan dit innovatieve online platform werken:&lt;/p&gt;&lt;p&gt;Ons webplatform biedt met een tourist experience en visitor’s journey de basis voor het creëren van belevenissen rondom plaatsen van betekenis, zowel met websites en applicaties als door de bezoeken aan die plaatsen. Een wegwijzer met beschrijvingen, verhalen, links, foto’s en video, gemaakt met en door het publiek.&lt;/p&gt;&lt;p&gt;We beginnen met Nederlandse plaatsen, maar breiden ons informatieaanbod geleidelijk uit naar andere Europese landen. De benodigde informatie verzamelen we door middel van user generated content.&lt;/p&gt;&lt;p&gt;Dit project komt tot stand onder auspiciën van het Amsterdam Museum, in samenwerking met o.a. het Nationaal Archief, de Rijksdienst Cultureel Erfgoed, Beeld&amp;amp;Geluid en vele andere erfgoedinstellingen.&lt;/p&gt;</drup:body>
    </record>
    <record id="24" entitytype="node" bundle="history_event">
      <drup:nodetype type="string">history_event</drup:nodetype>
      <drup:created type="date">2011-08-10T13:44:35Z</drup:created>
      <drup:changed type="date">2011-09-19T13:54:19Z</drup:changed>
      <drup:title type="text">80-jarige oorlog</drup:title>
      <drup:pathalias type="string">gebeurtenissen/80-jarige-oorlog</drup:pathalias>
      <drup:path type="string">node/24</drup:path>
      <drup:uri type="string">http://itin.dev/gebeurtenissen/80-jarige-oorlog</drup:uri>
      <drup:author_id type="int">1</drup:author_id>
      <drup:author_name type="text">cinnamon</drup:author_name>
      <drup:body type="text"></drup:body>
      <itin:sections type="string" entitytype="taxonomy_term" bundle="sections" id="28">oorlog</itin:sections>
      <itin:sections type="string" entitytype="taxonomy_term" bundle="sections" id="30">geloof</itin:sections>
      <itin:sections type="string" entitytype="taxonomy_term" bundle="sections" id="31">politiek</itin:sections>
      <itin:related_pvb type="link" entitytype="node" bundle="pvb" id="29" linkType="internal" pathalias="plaatsen/kasteel-wittem" path="node/29">Kasteel Wittem</itin:related_pvb>
      <itin:related_pvb type="link" entitytype="node" bundle="pvb" id="53" linkType="internal" pathalias="plaatsen/ruinekerk-bergen-nh" path="node/53">Ruïnekerk (Bergen - NH)</itin:related_pvb>
      <itin:related_pvb type="link" entitytype="node" bundle="pvb" id="209" linkType="internal" pathalias="plaatsen/slot-loevestein" path="node/209">Slot Loevestein</itin:related_pvb>
      <itin:related_pvb type="link" entitytype="node" bundle="pvb" id="244" linkType="internal" pathalias="plaatsen/bastion-baselaar-s-hertogenbosch" path="node/244">Bastion Baselaar ('s-Hertogenbosch)</itin:related_pvb>
      <itin:related_pvb type="link" entitytype="node" bundle="pvb" id="261" linkType="internal" pathalias="plaatsen/vesting-naarden" path="node/261">Vesting Naarden</itin:related_pvb>
      <itin:related_pvb type="link" entitytype="node" bundle="pvb" id="292" linkType="internal" pathalias="plaatsen/pater-vinck-toren" path="node/292">Pater Vinck Toren</itin:related_pvb>
      <itin:history_date_start type="date">1568-05-12T00:00:00Z</itin:history_date_start>
      <itin:history_date_end type="date">1648-04-01T00:00:00Z</itin:history_date_end>
      <itin:ref_period type="link" entitytype="node" bundle="history_period" id="56" linkType="internal" pathalias="perioden/gouden-eeuw" path="node/56">Gouden Eeuw</itin:ref_period>
      <itin:ref_persons type="link" entitytype="node" bundle="history_person" id="30" linkType="internal" pathalias="personen/willem-van-oranje" path="node/30">Willem van Oranje</itin:ref_persons>
      <itin:date_start type="date">1970-01-01T00:00:00Z</itin:date_start>
      <itin:date_year_start type="date">1970-01-01T00:00:00Z</itin:date_year_start>
      </record>
      </record-list>
      """
}
