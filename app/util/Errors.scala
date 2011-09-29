package util

import play.exceptions.PlayException

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class ProgrammerException(message: String) extends PlayException {
  def getErrorTitle = "Programmer error"

  def getErrorDescription = message
}