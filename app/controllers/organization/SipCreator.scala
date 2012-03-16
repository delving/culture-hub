package controllers.organization

import controllers.DelvingController
import play.api.mvc._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object SipCreator extends DelvingController {

  def index(orgId: String) = Root {
    Action {
      implicit request => Ok(Template('orgId -> orgId))
    }
  }

  def jnlp(user: String) = Root {
    Action {
      implicit request =>

        val sipCreatorVersion = "1.0.0"
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
              <j2se version="1.6+" initial-heap-size="256m" max-heap-size="512m"/>
              <property name="jnlp.versionEnabled" value="false"/>
              <jar href={"sip-app-" + sipCreatorVersion + "-SNAPSHOT.jar"} main="true"/>
              <jar href={"sip-core-" + sipCreatorVersion + "-SNAPSHOT.jar"}/>
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
              <jar href="groovy-all-1.8.5.jar"/>
              <jar href="woodstox-core-asl-4.0.9.jar"/>
              <jar href="stax-api-1.0-2.jar"/>
              <jar href="stax2-api-3.0.3.jar"/>
              <jar href="stringtemplate-3.0.jar"/>
              <jar href="antlr-2.7.7.jar"/>
              <jar href="cglib-2.1_3.jar"/>
              <jar href="asm-1.5.3.jar"/>
          </resources>
          <application-desc main-class="eu.delving.sip.Application">
            <argument>{user}</argument>
          </application-desc>
        </jnlp>

        Ok(jnlp).as("application/x-java-jnlp-file").withHeaders(("Cache-Control", "no-cache"))
    }
  }

}
