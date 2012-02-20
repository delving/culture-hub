package extensions

import java.io.InputStream
import java.security.{NoSuchAlgorithmException, MessageDigest}
import play.api.UnexpectedException
import io.Source
import org.apache.commons.codec.binary.{Hex, Base64}
import java.text.SimpleDateFormat
import java.util.{TimeZone, Locale, Properties}
import org.apache.commons.io.IOUtils

/**
 * Libs copy-pasted from Play 1
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

  def readContentAsString(is: InputStream): String = {
    readContentAsString(is, "utf-8")
  }

  /**
   * Read the Stream content as a string
   * @param is The stream to read
   * @return The String content
   */
  def readContentAsString(is: InputStream, encoding: String) = {
    try {
      IOUtils.toString(is, encoding);
    } catch {
      case e => throw new RuntimeException(e);
    } finally {
      try {
        is.close();
      } catch {
        case _ => //
      }
    }
  }

  // ~~~ play.libs.Codec

  def UUID: String = java.util.UUID.randomUUID().toString

  def encodeBASE64(value: java.io.File): String = new String(Base64.encodeBase64((Source.fromFile(value).map(_.toByte).toArray)))

  def encodeBASE64(value: String) = {
    try {
      new String(Base64.encodeBase64(value.getBytes("utf-8")))
    } catch {
      case e => throw UnexpectedException(unexpected = Some(e))
    }
  }

  /**
   * Build an hexadecimal MD5 hash for a String
   * @param value The String to hash
   * @return An hexadecimal Hash
   */
  def hexMD5(value: String) = {
    try {
      val messageDigest = MessageDigest.getInstance("MD5");
      messageDigest.reset();
      messageDigest.update(value.getBytes("utf-8"));
      val digest = messageDigest.digest();
      byteToHexString(digest);
    } catch {
      case ex => throw new UnexpectedException(Some(ex.getMessage));
    }
  }

  /**
   * Write a byte array as hexadecimal String.
   */
  def byteToHexString(bytes: Array[Byte]) = String.valueOf(Hex.encodeHex(bytes))


  // ~~~ play.libs.Crypto

  object HashType extends Enumeration {
    type HashType = Value
    val SHA512 = Value("SHA-512")
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

  // ~~~ play.Utils

  private val httpFormatter = new ThreadLocal[SimpleDateFormat]

  def getHttpDateFormatter = {
    if (httpFormatter.get() == null) {
      httpFormatter.set(new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US));
      httpFormatter.get().setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    httpFormatter.get();
  }


}