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

import java.io.Serializable;

/**
 * @author Gerald de Jong, Delving BV, <geralddejong@gmail.com>
 */

@XStreamAlias("tag")
public class Tag implements Comparable<Tag>, Serializable {

    @XStreamAsAttribute
    private String prefix;

    @XStreamAsAttribute
    private String localName;

    public static Tag create(String prefix, String localName) {
        return new Tag(prefix, localName);
    }

    public static Tag create(String tagString) {
        int colon = tagString.indexOf(':');
        if (colon < 0) {
            return create(null, tagString);
        }
        else {
            return create(tagString.substring(0, colon), tagString.substring(colon+1));
        }
    }

    private Tag(String prefix, String localName) {
        if (prefix != null && prefix.isEmpty()) {
            prefix = null;
        }
        this.prefix = prefix;
        this.localName = localName;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getLocalName() {
        return localName;
    }

    @Override
    public int compareTo(Tag tag) {
        if (prefix == null && tag.prefix != null) {
            return 1;
        }
        if (prefix != null && tag.prefix == null) {
            return -1;
        }
        if (prefix != null && tag.prefix != null) {
            int comp = prefix.compareTo(tag.prefix);
            if (comp != 0) {
                return comp;
            }
        }
        return localName.compareTo(tag.localName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tag tag = (Tag) o;
        return !(localName != null ? !localName.equals(tag.localName) : tag.localName != null) && !(prefix != null ? !prefix.equals(tag.prefix) : tag.prefix != null);
    }

    @Override
    public int hashCode() {
        int result = prefix != null ? prefix.hashCode() : 0;
        result = 31 * result + (localName != null ? localName.hashCode() : 0);
        return result;
    }

    public String toString() {
        if (prefix != null) {
            return prefix+":"+localName;
        }
        else {
            return localName;
        }
    }
}
