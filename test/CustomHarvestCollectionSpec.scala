import java.util.concurrent.TimeUnit
import models.HubUser
import play.api.test._
import play.api.test.Helpers._
import util.DomainConfigurationHandler

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class CustomHarvestCollectionSpec extends TestContext {

  step {
    loadStandalone()
  }

  "The CustomHarvestCollection module" should {

    "allow to create custom harvest collections" in {

      running(TestServer(3333), FIREFOX) {
        browser =>

          browser.goTo("http://delving.localhost:3333/login")
          browser.fill("#userName").`with`("bob")
          browser.fill("#password").`with`("secret")
          browser.$("#signin").click()
          browser.url must equalTo("http://delving.localhost:3333/")

          browser.goTo("http://delving.localhost:3333/organizations/delving/virtualCollection/add")
          browser.fill("#name").`with`("TestHarvestCollection")
          browser.fill("#spec").`with`("testHarvestCollection")
          browser.$("#token-input-dataSets").click()
          browser.$("#token-input-dataSets").text("PrincessehofSample")
          browser.executeScript("$('#dataSets').tokenInput('add', {id: 'PrincessehofSample', name: 'PrincessehofSample'})")

          browser.$("#saveButton").click()
          browser.await().atMost(20, TimeUnit.SECONDS).until("title").hasText("Virtual Collection TestHarvestCollection - Organization: delving")

          browser.url must equalTo("http://delving.localhost:3333/organizations/delving/virtualCollection/testHarvestCollection")


      }
    }
  }

  step {
    cleanup()
  }
}
