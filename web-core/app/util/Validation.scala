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

package util

import play.api.i18n.Messages
import collection.mutable.ArrayBuffer
import play.api.data.Form
import play.api.Logger
import collection.Seq

/**
 * Builds annotation rules for the jQuery form validation plugin, given a Form definition.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Validation {

  def getClientSideValidationRules(form: Form[AnyRef]): Map[String, String] = {

    //    form.mapping.mappings.foreach {
    //      m =>
    //        println(m.key)
    //    }

    val fieldConstraints = form.mapping.mappings.map(m => (m.key -> m.constraints))

    val validationRules: Seq[(String, String)] = for (f <- fieldConstraints) yield {
      val rules = new ArrayBuffer[String]
      val messages = new scala.collection.mutable.HashMap[String, String]

      for (c <- f._2) {
        c.name match {
          case Some("constraint.required") =>
            rules += "required: true"
            messages.put("required", Messages("error.required"))
          case Some("constraint.min") =>
            rules += "min: " + c.args(0)
            messages.put("min", Messages("error.min", c.args(0)))
          case Some("constraint.max") =>
            rules += "max: " + c.args(0)
            messages.put("max", Messages("error.max", c.args(0)))
          case Some("constraint.minLength") =>
            rules += "minLength: " + c.args(0)
            messages.put("minLength", Messages("error.minLength", c.args(0)))
          case Some("constraint.maxLength") =>
            rules += "maxLength: " + c.args(0)
            messages.put("maxLength", Messages("error.maxLength", c.args(0)))
          case Some("constraint.email") =>
            rules += "email: true"
            messages.put("email", Messages("error.email"))
          case _ => // ignore this for the time being
        }
      }

      (f._1, "{" + rules.mkString(",") + (if (!messages.isEmpty) { ", messages:{" + messages.map(pair => """"%s":"%s"""".format(pair._1, pair._2)).mkString(",") + "}" }) + "}")
    }

    validationRules.toMap[String, String]

  }

}