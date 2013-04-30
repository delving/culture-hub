package test

import com.gargoylesoftware.htmlunit.BrowserVersion
import concurrent.Await
import core.{ IndexingService, DomainServiceLocator, HubModule, HubServices }
import core.services.AggregatingOrganizationCollectionLookupService
import java.io.File
import models.HubMongoContext._
import org.openqa.selenium.htmlunit.HtmlUnitDriver
import org.specs2.mutable.Specification
import play.api.mvc.{ AsyncResult, Result }
import play.api.test._
import play.api.test.Helpers._
import util.{ TestDataLoader, OrganizationConfigurationHandler }
import xml.XML
import scala.concurrent.duration._
import models.OrganizationConfiguration

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait TestContext {

  protected lazy val indexingServiceLocator = HubModule.inject[DomainServiceLocator[IndexingService]](name = None)

  val SAMPLE_A = "sample-a"
  val SAMPLE_B = "sample-b"

  def asyncToResult(response: Result) = Await.result(response.asInstanceOf[AsyncResult].result, 3 seconds)

  def contentAsXML(response: Result) = XML.loadString(contentAsString(response))

  def applicationPath = if (new File(".").listFiles().exists(
    f => f.isDirectory && f.getName == "conf")) new File(".").getAbsoluteFile
  else new File("culture-hub")

  def withTestConfig[T](block: => T): T = withTestConfig(_ => block)

  def withTestConfig[T](block: OrganizationConfiguration => T): T = withTestConfig(Map.empty[String, AnyRef])(block)

  def withTestConfig[T](additionalConfiguration: Map[String, _])(block: OrganizationConfiguration => T): T = {
    running(FakeApplication(path = applicationPath, additionalConfiguration = additionalConfiguration, withoutPlugins = Seq("play.api.db.BoneCPPlugin", "play.db.ebean.EbeanPlugin", "play.db.jpa.JPAPlugin", "play.api.db.evolutions.EvolutionsPlugin"))) {
      implicit val configuration = OrganizationConfigurationHandler.getByOrgId("delving")
      block(configuration)
    }
  }

  def withTestData[T](samples: String*)(block: => T): T = {
    withTestConfig {
      load(Map("samples" -> samples))
      try {
        block
      } finally {
        cleanup()
      }
    }
  }

  def load(parameters: Map[String, Seq[String]]) {
    TestDataLoader.load(parameters)
  }

  def cleanup(cleanupSOLR: Boolean = true) {
    withTestConfig {
      HubServices.init()
      implicit val configuration = OrganizationConfigurationHandler.getByOrgId("delving")
      try {
        val specsToFix = List(SAMPLE_A, SAMPLE_B)
        specsToFix.foreach(spec =>
          (new AggregatingOrganizationCollectionLookupService()).findBySpecAndOrgId(spec, "delving").map {
            set =>
              HubServices.basexStorages.getResource(configuration).withSession(set) {
                session =>
                  {
                    val r = session.execute("drop database delving____" + spec)
                    println(r)
                  }
              }
          }
        )
      } catch {
        case t: Throwable => // ignore if not found
      }
      createConnection(configuration.mongoDatabase).dropDatabase()
      createConnection(configuration.objectService.fileStoreDatabaseName).dropDatabase()
      createConnection(configuration.objectService.imageCacheDatabaseName).dropDatabase()
      if (cleanupSOLR) {
        indexingServiceLocator.byDomain(configuration).deleteByQuery("*:*")
      }
    }
  }

  def loadStandalone(samples: String*) {
    withTestConfig {
      load(Map("samples" -> samples))
    }
  }

}

trait Specs2TestContext extends Specification with TestContext {

  args(sequential = true)

}

class FirefoxHtmlUnitDriver extends HtmlUnitDriver(BrowserVersion.FIREFOX_3_6)