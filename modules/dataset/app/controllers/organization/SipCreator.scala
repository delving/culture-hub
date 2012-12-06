package controllers.organization

import play.api.mvc._
import eu.delving.culturehub.BuildInfo
import controllers.OrganizationController

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object SipCreator extends OrganizationController {

  def index(orgId: String) = OrganizationMember {
    Action {
      implicit request => Ok(Template('orgId -> orgId))
    }
  }

  def jnlp(user: String) = Root {
    Action {
      implicit request =>

        val host = request.domain + ":9000"
        val home = "http://" + host + "/" + user + "/"
        val codebase = "http://" + host + "/assets/sip-creator/"

        val jnlp = <jnlp spec="1.0+" codebase={codebase} href={home + "sip-creator.jnlp"}>
          <information>
            <title>SIP-Creator</title>
            <vendor>Delving</vendor>
            <description kind="one-line">SIP-Creator</description>
            <description kind="short">Submission Information Package Creator</description>
            <icon href={codebase + "sip-creator-logo.png"} kind="default"/>
            <shortcut online="false">
              <desktop/>
            </shortcut>
          </information>
          <security>
            <all-permissions/>
          </security>
          <resources>
            <j2se version="1.6+" initial-heap-size="512m" max-heap-size="1024m" java-vm-args="-Dfile.encoding=UTF-8"/>
            <property name="jnlp.versionEnabled" value="false"/>
            <jar href={"sip-app-" + BuildInfo.sipApp + ".jar"} main="true"/>
            <jar href={"sip-core-" + BuildInfo.sipCore + ".jar"}/>
            <jar href={"schema-repo-" + BuildInfo.schemaRepo + ".jar"}/>
            <jar href="oauth2-client-0.2-SNAPSHOT.jar"/>
            <jar href="oauth2-common-0.2-SNAPSHOT.jar"/>
            <jar href="jettison-1.2.jar"/>
            <jar href="slf4j-api-1.6.1.jar"/>
            <jar href="httpclient-4.1.2.jar"/>
            <jar href="httpcore-4.1.2.jar"/>
            <jar href="commons-logging-1.1.1.jar"/>
            <jar href="commons-codec-1.4.jar"/>
            <jar href="log4j-1.2.16.jar"/>
            <jar href="commons-lang-2.3.jar"/>
            <jar href="commons-io-2.0.jar"/>
            <jar href="xstream-1.4.2.jar"/>
            <jar href="xmlpull-1.1.3.1.jar"/>
            <jar href="xpp3_min-1.1.4c.jar"/>
            <jar href="gson-1.7.1.jar"/>
            <jar href="groovy-all-2.0.0.jar"/>
            <jar href="woodstox-core-asl-4.0.9.jar"/>
            <jar href="stax-api-1.0-2.jar"/>
            <jar href="stax2-api-3.0.3.jar"/>
            <jar href="jfreechart-1.0.13.jar"/>
            <jar href="jcommon-1.0.16.jar"/>
            <jar href="itext-2.1.7.jar"/>
            <jar href="bcmail-jdk14-138.jar"/>
            <jar href="bcprov-jdk14-138.jar"/>
            <jar href="bctsp-jdk14-1.38.jar"/>
            <jar href="bcprov-jdk14-1.38.jar"/>
            <jar href="bcmail-jdk14-1.38.jar"/>
            <jar href="stringtemplate-3.0.jar"/>
            <jar href="antlr-2.7.7.jar"/>
            <jar href="cglib-2.1_3.jar"/>
            <jar href="asm-1.5.3.jar"/>
            <jar href="jcoord-1.0.jar"/>
            <jar href="proj4j-0.1.0.jar"/>
            <jar href="sqljdbc4-3.0.jar"/>
            <jar href="jgoodies-forms-1.6.0.jar"/>
            <jar href="jgoodies-common-1.4.0.jar"/>
          </resources>
          <application-desc main-class="eu.delving.sip.Application">
            <argument>{user}</argument> <!--Never add spaces between the user and the tags. This creates unwanted behaviour in creating the Sip-Creator workspaces-->
          </application-desc>
        </jnlp>

        Ok(jnlp).as("application/x-java-jnlp-file").withHeaders(("Cache-Control", "no-cache"))
    }
  }

}
