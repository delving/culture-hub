package eu.delving

import org.scalatest.{ FunSuite, BeforeAndAfterAll}
import org.mortbay.jetty.{ Server}
import org.mortbay.jetty.webapp.{ WebAppContext }
import org.openqa.selenium.server.RemoteControlConfiguration
import org.openqa.selenium.server.SeleniumServer
import com.thoughtworks.selenium._

/**
 * Selenium test example, to test the GUI interaction directly
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class SeleniumTest extends FunSuite with BeforeAndAfterAll {

  private var server         : Server          = null
  private var selenium       : DefaultSelenium = null
  private var seleniumserver : SeleniumServer  = null

  override def beforeAll() {
    /*  This code takes care of the following:

        1. Start an instance of your web application
        2. Start an instance of the Selenium backend
        3. Start an instance of the Selenium client
    */
    val GUI_PORT             = 8080
    val SELENIUM_SERVER_PORT = 4444

    // Setting up the jetty instance which will be running the
    // GUI for the duration of the tests
    server  = new Server(GUI_PORT)
    val context = new WebAppContext()
    context.setServer(server)
    context.setContextPath("/")
    context.setWar("src/main/webapp")
    server.addHandler(context)
    server.start()

    // Setting up the Selenium Server for the duration of the tests
    val rc = new RemoteControlConfiguration()
    rc.setPort(SELENIUM_SERVER_PORT)
    seleniumserver = new SeleniumServer(rc)
    seleniumserver.boot()
    seleniumserver.start()
    seleniumserver.getPort()

    // Setting up the Selenium Client for the duration of the tests
    selenium = new DefaultSelenium("localhost", SELENIUM_SERVER_PORT, "*firefox", "http://localhost:"+GUI_PORT+"/")
    selenium.start()
  }

  override def afterAll() {
    // Close everyhing when done
    selenium.close()
    server.stop()
    seleniumserver.stop()
  }

  test("Test that a user is properly displayed with all his stuff") {
    selenium.open("/john")
    assert(selenium.isTextPresent("The user john and all his stuff"), true)
  }

}