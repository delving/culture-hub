package core

import xml.Elem
import scala.collection.immutable.ListMap

/**
 * Describes an API parameter
 */
case class ExplainItem(label: String, options: List[String] = List(), description: String = "") {

  def toXml: Elem = {
    <element>
      <label>
        { label }
      </label>{
        if (!options.isEmpty)
          <options>
            {
              options.map(option => <option>
                                      { option }
                                    </option>)
            }
          </options>
      }{
        if (!description.isEmpty) <description>
                                    { description }
                                  </description>
      }
    </element>
  }

  def toJson: ListMap[String, Any] = {
    if (!options.isEmpty && !description.isEmpty)
      ListMap("label" -> label, "options" -> options.toSeq, "description" -> description)
    else if (!options.isEmpty)
      ListMap("label" -> label, "options" -> options.toSeq)
    else
      ListMap("label" -> label)
  }
}