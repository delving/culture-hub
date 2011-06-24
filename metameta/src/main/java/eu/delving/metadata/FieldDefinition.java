/*
 * Copyright 2010 DELVING BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.metadata;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

import java.util.Iterator;
import java.util.List;

/**
 * An XStream approach for replacing the annotated beans.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

@XStreamAlias("field")
public class FieldDefinition implements Comparable<FieldDefinition> {

    public FieldDefinition() {
        fullDoc = true;
    }

    @XStreamAsAttribute
    public String prefix;

    @XStreamAsAttribute
    public String localName;

    @XStreamAsAttribute
    public boolean briefDoc;

    @XStreamAsAttribute
    public boolean fullDoc;

    @XStreamAsAttribute
    public boolean systemField;

    @XStreamAsAttribute
    public String fieldType;

    @XStreamAsAttribute
    public String facetPrefix;

    @XStreamAsAttribute
    public String facetName;

    @XStreamAsAttribute
    public String searchField;

    public Validation validation;

    @XStreamOmitField
    public Path path;

    public String description;

    @XStreamOmitField
    private Tag tag;

    public Tag getTag() {
        if (tag == null) {
            tag = Tag.create(prefix, localName);
        }
        return tag;
    }

    public void setPath(Path path) {
        path.push(getTag());
        this.path = new Path(path);
        path.pop();
    }

    public String getFieldNameString() {
        if (getPrefix() == null) {
            return tag.getLocalName();
        }
        else {
            return getPrefix() + '_' + tag.getLocalName();
        }
    }

    public String getPrefix() {
        return tag.getPrefix();
    }

    public String getLocalName() {
        return tag.getLocalName();
    }

    public String getFacetName() {
        return tag.getLocalName().toUpperCase();
    }

    @Override
    public String toString() {
        return String.format("FieldDefinition(%s)", path);
    }

    @Override
    public int compareTo(FieldDefinition fieldDefinition) {
        return path.compareTo(fieldDefinition.path);
    }

    public String addOptionalConverter(String variable) {
        if (validation != null && validation.converter != null) {
            return variable + validation.converter.call;
        }
        else {
            return variable;
        }
    }

    @XStreamAlias("validation")
    public static class Validation {

        public Validation() {
            multivalued = true;
            required = true;
        }

        @XStreamAsAttribute
        public String factName;

        @XStreamAsAttribute
        public String requiredGroup;

        @XStreamAsAttribute
        public boolean url;

        @XStreamAsAttribute
        public boolean object;

        @XStreamAsAttribute
        public boolean unique;

        @XStreamAsAttribute
        public boolean id;

        @XStreamAsAttribute
        public boolean type;

        @XStreamAsAttribute
        public boolean multivalued;

        @XStreamAsAttribute
        public boolean required;

        public List<String> options;

        public Converter converter;

        @XStreamOmitField
        public FactDefinition factDefinition;

        public boolean hasOptions() {
            return options != null || factDefinition != null && factDefinition.options != null;
        }

        public boolean allowOption(String value) {
            for (String option : getOptions()) {
                if (option.endsWith(":")) {
                    int colon = value.indexOf(':');
                    if (colon > 0) {
                        if (option.equals(value.substring(0, colon + 1))) {
                            return true;
                        }
                    }
                    else {
                        if (option.equals(value) || option.substring(0, option.length() - 1).equals(value)) {
                            return true;
                        }
                    }
                }
                else if (option.equals(value)) {
                    return true;
                }
            }
            return false;
        }

        public String getOptionsString() {
            StringBuilder enumString = new StringBuilder();
            Iterator<String> walk = getOptions().iterator();
            while (walk.hasNext()) {
                enumString.append(walk.next());
                if (walk.hasNext()) {
                    enumString.append(',');
                }
            }
            return enumString.toString();
        }

        public List<String> getOptions() {
            if (options != null) {
                return options;
            }
            else if (factDefinition != null) {
                return factDefinition.options;
            }
            else {
                return null;
            }
        }
    }

    public static class Converter {

        @XStreamAsAttribute
        public boolean multipleOutput;

        @XStreamAsAttribute
        public String call;
    }
}
