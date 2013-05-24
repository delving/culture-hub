import core.HubServices
import org.openqa.selenium.chrome.ChromeDriver
import play.api.i18n.{ Messages, Lang }
import play.api.test._
import play.api.test.Helpers._
import test.Specs2TestContext
import util.OrganizationConfigurationHandler

/**
 * Testing the registration work-flow
 *
 * TODO check that a user can't be created twice
 * TODO check that password recovery works
 * TODO check other things along the way
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class RegistrationSpec extends test.Specs2TestContext {

  "The Registration" should {

    "work" in {

      System.setProperty("webdriver.chrome.driver", System.getProperty("user.home") + "/chromedriver")

      running(TestServer(3333, FakeApplication(path = applicationPath)), classOf[ChromeDriver]) {
        browser =>
          registerAlice(browser)
          activateAlice(browser)
          loginAlice(browser)
      }
    }
  }

  def registerAlice(browser: TestBrowser) = {
    browser.goTo("http://delving.localhost:3333/registration")

    browser.fill("#firstName").`with`("Alice")
    browser.fill("#lastName").`with`("Smith")
    browser.fill("#email").`with`("alice@smith.com")
    browser.fill("#userName").`with`("alice")
    browser.fill("#password1").`with`("alice")
    browser.fill("#password2").`with`("alice")
    browser.fill("#code").`with`("test")

    browser.$("#submit").click()
    browser.url must equalTo("http://delving.localhost:3333/")

    val msg = Messages("hub.YourAccountWasCreated", "alice@smith.com")(Lang("en"))
    browser.$(".alert-success").first.getText must equalTo(msg)

  }

  def activateAlice(browser: TestBrowser) = {
    browser.goTo("http://delving.localhost:3333/registration/activate/TESTACTIVATION")
    browser.url must equalTo("http://delving.localhost:3333/")
    val msg = Messages("hub.YourAccountIsNowActive")(Lang("en"))
    browser.$(".alert-success").first.getText must equalTo(msg)

    implicit val configuration = OrganizationConfigurationHandler.getByOrgId("delving")
    HubServices.registrationServiceLocator.byDomain.isAccountActive("alice@smith.com") must beTrue
  }

  def loginAlice(browser: TestBrowser) = {
    browser.goTo("http://delving.localhost:3333/login")
    browser.fill("#userName").`with`("alice")
    browser.fill("#password").`with`("alice")
    browser.$("#signin").click()
    browser.url must equalTo("http://delving.localhost:3333/")
  }

}