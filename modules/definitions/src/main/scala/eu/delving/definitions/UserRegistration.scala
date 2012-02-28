package eu.delving.definitions

/**
 * User registration request definition
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class UserRegistration(userName: String,
                            firstName: String,
                            lastName: String,
                            email: String)