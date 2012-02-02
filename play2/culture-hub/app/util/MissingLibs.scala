package util

import java.io.InputStream
import java.util.Properties
import java.security.{NoSuchAlgorithmException, MessageDigest}
import org.apache.commons.codec.binary.Base64

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object MissingLibs {

  // ~~~ play.libs.IO

  def readUtf8Properties(is: InputStream): Properties = {
    val properties = new Properties()
    try {
      properties.load(is)
      is.close()
    } catch {
      case e => throw new RuntimeException(e)
    }
    properties
  }

  // ~~~ play.libs.Crypto

  object HashType extends Enumeration {
    type HashType = Value
    val SHA512 = Value("SHA512")
  }

  def passwordHash(input: String, hashType: HashType.Value) = {
    try {
      val m = MessageDigest.getInstance(hashType.toString);
      val out = m.digest(input.getBytes);
      new String(Base64.encodeBase64(out));
    } catch {
      case e: NoSuchAlgorithmException => throw new RuntimeException(e);
    }
  }

}