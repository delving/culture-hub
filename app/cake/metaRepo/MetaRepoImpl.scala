package cake.metaRepo

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 7/7/11 9:27 AM  
 */

class MetaRepoImpl  { // extends MetaRepo

}

class AccessKeyException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this(s, null)
}

class BadArgumentException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this(s, null)
}

class DataSetNotFoundException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this(s, null)
}

class HarvindexingException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this(s, null)
}

class MappingNotFoundException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this(s, null)
}

class MetaRepoSystemException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this(s, null)
}

class RecordParseException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this(s, null)
}

class ResumptionTokenNotFoundException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this(s, null)
}