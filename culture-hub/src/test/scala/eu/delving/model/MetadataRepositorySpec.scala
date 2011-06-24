package eu.delving.model

import org.scalatest.matchers.MustMatchers
import org.scalatest.{FeatureSpec, GivenWhenThen}
import net.liftweb.mongodb.{MongoDB, DefaultMongoIdentifier, MongoAddress, MongoHost}
import java.util.Date

/**
 * todo: javadoc
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */


class MetadataRepositorySpec extends FeatureSpec with GivenWhenThen with MustMatchers {
  feature("Storage of metadata") {
    MongoDB.defineDb(
      DefaultMongoIdentifier,
      MongoAddress(MongoHost(), "lift_services")
    )
    scenario("create a dataset") {
      DataSet.createRecord.spec("spek").state(DataSetState.Incomplete).sourceHash("ABC").save
    }
    scenario("create records") {
      DataRecordCollection("spek").createRecord.unique("U Nork").modified(new Date()).xml("<bite me/>").save
    }
  }
}