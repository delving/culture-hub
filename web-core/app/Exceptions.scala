/*
 * Copyright 2012 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package exceptions {

  // ~~~ auth

  class AccessKeyException(s: String, throwable: Throwable) extends Exception(s, throwable) {
    def this(s: String) = this(s, null)
  }

  class UnauthorizedException(s: String, throwable: Throwable) extends Exception(s, throwable) {
    def this(s: String) = this(s, null)
  }

  // ~~~ OAI-PMH

  class BadArgumentException(s: String, throwable: Throwable) extends Exception(s, throwable) {
    def this(s: String) = this(s, null)
  }

  class DataSetNotFoundException(s: String, throwable: Throwable) extends Exception(s, throwable) {
    def this(s: String) = this(s, null)
  }

  class RecordNotFoundException(s: String, throwable: Throwable) extends Exception(s, throwable) {
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

  class InvalidIdentifierException(s: String, throwable: Throwable) extends Exception(s, throwable) {
    def this(s: String) = this(s, null)
  }

  // ~~~ DataSet

  class MappingNotFoundException(s: String, throwable: Throwable) extends Exception(s, throwable) {
    def this(s: String) = this(s, null)
  }

  // ~~~ SOLR

  class SolrConnectionException(s: String, throwable: Throwable) extends Exception(s, throwable) {
    def this(s: String) = this(s, null)
  }

  case class MalformedQueryException(s: String, throwable: Throwable) extends Exception(s, throwable) {
    def this(s: String) = this(s, null)
  }

  // ~~~ BaseX insertion

  class StorageInsertionException(s: String, throwable: Throwable) extends Exception(s, throwable)

}