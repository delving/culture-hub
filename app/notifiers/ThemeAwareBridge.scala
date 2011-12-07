/*
 * Copyright 2011 Delving B.V.
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

package notifiers

import controllers.ThemeAware
import play.mvc.Controller

/**
 * Bridge object to use from Java to get access to the Themes context.
 * Do call before() and after() at each use!
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object ThemeAwareBridge extends Controller with ThemeAware {

  def before() { setTheme() }
  def after() { cleanup() }

}