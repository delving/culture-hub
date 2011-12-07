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

import play.i18n.Messages
import java.lang.reflect.Field
import play.data.validation.Annotations._
import collection.mutable.ArrayBuffer

/**
 * Builds annotation rules for the jQuery form validation plugin, given a case class annotated with Play validation annotations.
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Validation {

  def getClientSideValidationRules(clazz: Class[_]): Map[String, String] =
    clazz.getDeclaredFields.map(f => (f.getName, buildValidationRuleString(f))).toMap

  private def buildValidationRuleString(implicit field: Field) = {
    val rules = new ArrayBuffer[String]
    val messages = new scala.collection.mutable.HashMap[String, String]

    Option(field.getAnnotation(classOf[play.data.validation.Required])).foreach(required => {
      rules += "required: true"
      if (required.message() != null) messages.put("required", Messages.get(required.message))
    })

    Option(field.getAnnotation(classOf[play.data.validation.Min])).foreach(min => {
      rules += "min: " + min.value()
      if (min.message() != null) messages.put("min", Messages.get(min.message, min.value.asInstanceOf[AnyRef]))
    })

    Option(field.getAnnotation(classOf[play.data.validation.Max])).foreach(max => {
      rules += "max: " + max.value()
      if (max.message() != null) messages.put("max", Messages.get(max.message, max.value.asInstanceOf[AnyRef]))
    })

    Option(field.getAnnotation(classOf[play.data.validation.Range])).foreach(range => {
      rules += "range:[%s, %s]".format(range.min, range.max)
      if (range.message() != null) messages.put("range", Messages.get(range.message, range.min.asInstanceOf[AnyRef], range.max.asInstanceOf[AnyRef]))
    })

    Option(field.getAnnotation(classOf[play.data.validation.MaxSize])).foreach(maxSize => {
      rules += "maxLength: " + maxSize.value()
      if (maxSize.message() != null) messages.put("maxSize", Messages.get(maxSize.message, maxSize.value.asInstanceOf[AnyRef]))
    })

    Option(field.getAnnotation(classOf[play.data.validation.MinSize])).foreach(minSize => {
      rules += "minLength: " + minSize.value()
      if (minSize.message() != null) messages.put("minSize", Messages.get(minSize.message, minSize.value.asInstanceOf[AnyRef]))
    })

    Option(field.getAnnotation(classOf[play.data.validation.URL])).foreach(url => {
      rules += "url: true"
      if (url.message() != null) messages.put("url", Messages.get(url.message))
    })

    Option(field.getAnnotation(classOf[play.data.validation.Email])).foreach(email => {
      rules += "email: true"
      if (email.message() != null) messages.put("email", Messages.get(email.message))
    })

    "{" + rules.mkString(",") + (if (!messages.isEmpty) { ", messages:{" + messages.map(pair => """"%s":"%s"""".format(pair._1, pair._2)).mkString(",") + "}" }) + "}"

  }

}