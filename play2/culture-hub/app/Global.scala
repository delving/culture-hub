/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

import play.api._
import util.ThemeHandler

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    println("""
              ____       __      _
             / __ \___  / /   __(_)___  ____ _
            / / / / _ \/ / | / / / __ \/ __ `/
           / /_/ /  __/ /| |/ / / / / / /_/ /
          /_____/\___/_/ |___/_/_/ /_/\__, /
                                     /____/
             ______      ____                  __  __      __
            / ____/_  __/ / /___  __________  / / / /_  __/ /_
           / /   / / / / / __/ / / / ___/ _ \/ /_/ / / / / __ \
          / /___/ /_/ / / /_/ /_/ / /  /  __/ __  / /_/ / /_/ /
          \____/\__,_/_/\__/\__,_/_/   \___/_/ /_/\__,_/_.___/
    """)

    // load themes
    ThemeHandler.startup()

  }



}