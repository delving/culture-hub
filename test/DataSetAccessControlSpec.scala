import models.DataSet
import org.scalatest.matchers.ShouldMatchers
import play.test.UnitFlatSpec
import util.TestDataGeneric

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class DataSetAccessControlSpec extends UnitFlatSpec with ShouldMatchers with TestDataGeneric {

  it should "tell if a user has read access" in {
    DataSet.canRead("Verzetsmuseum", "jimmy", "cultureHub") should be(true)
    DataSet.canRead("Verzetsmuseum", "bob", "cultureHub") should be(true)
  }
  it should "tell if a user has update access" in {
    DataSet.canUpdate("Verzetsmuseum", "jimmy", "cultureHub") should be(true)
    DataSet.canUpdate("Verzetsmuseum", "bob", "cultureHub") should be(false)
  }
  it should "tell if a user has delete access" in {
    DataSet.canDelete("Verzetsmuseum", "jimmy", "cultureHub") should be(true)
    DataSet.canDelete("Verzetsmuseum", "bob", "cultureHub") should be(false)
  }
  it should "tell if a user owns the object" in {
    DataSet.owns("Verzetsmuseum", "jimmy", "cultureHub") should be(true)
    DataSet.owns("Verzetsmuseum", "bob", "cultureHub") should be(false)
  }

  it should "update rights of an existing user" in {
    DataSet.canDelete("Verzetsmuseum", "bob", "cultureHub") should be(false)
    DataSet.canUpdate("Verzetsmuseum", "bob", "cultureHub") should be(false)
    DataSet.addAccessRight("Verzetsmuseum", "bob", "cultureHub", "delete" -> true, "update" -> true)
    DataSet.canDelete("Verzetsmuseum", "bob", "cultureHub") should be(true)
    DataSet.canUpdate("Verzetsmuseum", "bob", "cultureHub") should be(true)
    DataSet.canRead("Verzetsmuseum", "bob", "cultureHub") should be(true)
    DataSet.owns("Verzetsmuseum", "bob", "cultureHub") should be(false)
  }

  it should "add rights for a non-existing user" in {
    DataSet.canRead("Verzetsmuseum", "jane", "cultureHub") should be(false)
    DataSet.addAccessRight("Verzetsmuseum", "jane", "cultureHub", "read" -> true)
    DataSet.canRead("Verzetsmuseum", "jane", "cultureHub") should be(true)
    DataSet.canUpdate("Verzetsmuseum", "jane", "cultureHub") should be(false)
    DataSet.canDelete("Verzetsmuseum", "jane", "cultureHub") should be(false)
    DataSet.owns("Verzetsmuseum", "jane", "cultureHub") should be(false)
  }

// TODO put this back when we use AccessControl again
//  it should "tell if a user has read access via a group" in {
//    DataSet.canRead("Verzetsmuseum", "dan", "cultureHub") should be(true)
//  }

}
