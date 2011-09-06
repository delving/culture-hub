package notifiers

import controllers.ThemeAware

/**
 * Bridge object to use from Java to get access to the Themes context.
 * Do call before() and after() at each use!
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object ThemeAwareBridge extends ThemeAware {

  def before() { setTheme() }
  def after() { cleanup() }

}