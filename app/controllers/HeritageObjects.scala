package controllers

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

import util.Constants._
import play.mvc.results.Result


/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object HeritageObjects extends DelvingController {

  def list(user: Option[String], page: Int = 1): Result = {
    val browser: (List[ListItem], Int) = Search.browse(MDR, user, request, theme)
    Template("/list.html", 'title -> listPageTitle("mdr"), 'itemName -> MDR, 'items -> browser._1, 'page -> page, 'count -> browser._2)
  }

}