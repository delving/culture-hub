package util

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Implicits {

  implicit def stringUtils(s: String) = new {
    def pluralize = if(!s.endsWith("y")) s + "s" else s.substring(0, s.length() - 1) + "ies"
  }


}