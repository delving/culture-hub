import java.util.concurrent.TimeUnit
import play.api.test._
import play.api.test.Helpers._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class CustomHarvestCollectionSpec extends TestContext {

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
          browser.await().atMost(10, TimeUnit.SECONDS).until("title").hasText("Virtual Collection TestHarvestCollection - Organization: delving")

          browser.url must equalTo("http://delving.localhost:3333/organizations/delving/virtualCollection/testHarvestCollection")
        //          browser.$("#title").getTexts().get(0) must equalTo("Hello Coco")


      }
    }
  }
}
