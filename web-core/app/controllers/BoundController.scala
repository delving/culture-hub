package controllers

import com.escalatesoft.subcut.inject.{ Injectable, BindingModule }

/**
 * Base class allowing us to use subcut within controllers
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
abstract class BoundController(val binding: BindingModule) extends Injectable {
  implicit val bindingModule = binding

}
