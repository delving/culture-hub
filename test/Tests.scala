import play._
import play.test._

import org.scalatest._
import org.scalatest.junit._
import org.scalatest.matchers._

class BasicTests extends UnitFlatSpec with ShouldMatchers {
    
    it should "run this dumb test" in {
        (1 + 1) should be (2)
    }

}