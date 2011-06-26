/*
 * Copyright 2007 EDL FOUNDATION
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

package eu.europeana.sip.core;

import eu.delving.metadata.Sanitizer;
import groovy.lang.DelegatingMetaClass;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.xml.QName;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A simpler adaptation of groovy.util.Node
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

@SuppressWarnings("unchecked")
public class GroovyNode {

    private GroovyNode parent;

    private QName qName;

    private String stringName;

    private Map<String, String> attributes;

    private Object value;

    public GroovyNode(GroovyNode parent, String namespaceUri, String localName, String prefix) {
        this(parent, new QName(namespaceUri, localName, prefix));
    }

    public GroovyNode(GroovyNode parent, String name) {
        this(parent, new QName(name), new GroovyList());
    }

    public GroovyNode(GroovyNode parent, QName qName) {
        this(parent, qName, new GroovyList());
    }

    public GroovyNode(GroovyNode parent, String name, String value) {
        this(parent, new QName(name), new TreeMap<String, String>(), value);
    }

    public GroovyNode(GroovyNode parent, QName qName, Object value) {
        this(parent, qName, new TreeMap<String, String>(), value);
    }

    public GroovyNode(GroovyNode parent, QName qName, Map<String, String> attributes, Object value) {
        this.parent = parent;
        this.qName = qName;
        this.attributes = attributes;
        this.value = value;
        if (parent != null) {
            getParentList(parent).add(this);
        }
    }

    public String text() {
        if (value instanceof String) {
            return (String) value;
        }
        else if (value instanceof Collection) {
            Collection coll = (Collection) value;
            String previousText = null;
            StringBuffer buffer = null;
            for (Object child : coll) {
                if (child instanceof String) {
                    String childText = (String) child;
                    if (previousText == null) {
                        previousText = childText;
                    }
                    else {
                        if (buffer == null) {
                            buffer = new StringBuffer();
                            buffer.append(previousText);
                        }
                        buffer.append(childText);
                    }
                }
            }
            if (buffer != null) {
                return buffer.toString();
            }
            else {
                if (previousText != null) {
                    return previousText;
                }
            }
        }
        return "";
    }

    public GroovyList children() {
        return (value instanceof GroovyList) ? (GroovyList) value : new GroovyList(value);
    }

    public Map<String, String> attributes() {
        return attributes;
    }

    public QName qName() {
        return qName;
    }

    public String name() {
        if (stringName == null) {
            if (qName.getPrefix().isEmpty()) {
                stringName = Sanitizer.tagToVariable(qName.getLocalPart());
            }
            else {
                stringName = qName.getPrefix() + "_" + Sanitizer.tagToVariable(qName.getLocalPart());
            }
        }
        return stringName;
    }

    public Object value() {
        return value;
    }

    public int size() {
        return text().length();
    }

    public boolean contains(String s) {
        return text().contains(s);
    }

    public String [] split(String s) {
        return text().split(s);
    }

    public boolean endsWith(String s) {
        return text().endsWith(s);
    }

    public String replaceAll(String from, String to) {
        return text().replaceAll(from, to);
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public GroovyNode parent() {
        return parent;
    }

    /**
     * Provides lookup of elements by non-namespaced name
     *
     * @param key the name (or shortcut key) of the node(s) of interest
     * @return the nodes which match key
     */
    public Object get(String key) {
        if (key != null && key.charAt(0) == '@') {
            String attributeName = key.substring(1);
            return attributes().get(attributeName);
        }
        if ("..".equals(key)) {
            return parent();
        }
        if ("*".equals(key)) {
            return children();
        }
        if ("_".equals(key)) {
            return getValueNodes();
        }
        return getByName(key);
    }

    public String toString() {
        return text();
    }

    // privates ===================================================================================

    private List<Object> getParentList(GroovyNode parent) {
        Object parentValue = parent.value();
        List<Object> parentList;
        if (parentValue instanceof List) {
            parentList = (List<Object>) parentValue;
        }
        else {
            parentList = new GroovyList();
            parentList.add(parentValue);
            parent.setValue(parentList);
        }
        return parentList;
    }

    private GroovyList getByName(String name) {
        GroovyList answer = new GroovyList();
        for (Object child : children()) {
            if (child instanceof GroovyNode) {
                GroovyNode childNode = (GroovyNode) child;
                if (childNode.value() instanceof List && ((List) childNode.value).isEmpty()) {
                    continue;
                }
                Object childNodeName = childNode.name();
                if (childNodeName instanceof QName) {
                    QName qn = (QName) childNodeName;
                    if (qn.matches(name)) {
                        answer.add(childNode);
                    }
                }
                else if (name.equals(childNodeName)) {
                    answer.add(childNode);
                }
            }
        }
        return answer;
    }

    private List<Object> getValueNodes() {
        GroovyList answer = new GroovyList();
        getValueNodes(answer);
        return answer;
    }

    private void getValueNodes(GroovyList answer) {
        if (value instanceof GroovyList) {
            for (Object object : ((GroovyList) value)) {
                if (object instanceof GroovyNode) {
                    ((GroovyNode) object).getValueNodes(answer);
                }
                else {
                    getValueNodes((GroovyList) object);
                }
            }
        }
        else if (value instanceof String && !((String) value).trim().isEmpty()) {
            answer.add(this);
        }
        else {
            throw new RuntimeException(value.getClass().getName());
        }
    }

    protected static void setMetaClass(final MetaClass metaClass, Class nodeClass) {
        final MetaClass newMetaClass = new DelegatingMetaClass(metaClass) {
            @Override
            public Object getAttribute(final Object object, final String attribute) {
                GroovyNode n = (GroovyNode) object;
                return n.get("@" + attribute);
            }

            @Override
            public void setAttribute(final Object object, final String attribute, final Object newValue) {
                GroovyNode n = (GroovyNode) object;
                n.attributes().put(attribute, (String) newValue);
            }

            @Override
            public Object getProperty(Object object, String property) {
                if (object instanceof GroovyNode) {
                    GroovyNode n = (GroovyNode) object;
                    return n.get(property);
                }
                return super.getProperty(object, property);
            }

            @Override
            public void setProperty(Object object, String property, Object newValue) {
                if (property.startsWith("@")) {
                    String attribute = property.substring(1);
                    GroovyNode n = (GroovyNode) object;
                    n.attributes().put(attribute, (String) newValue);
                    return;
                }
                delegate.setProperty(object, property, newValue);
            }

        };
        GroovySystem.getMetaClassRegistry().setMetaClass(nodeClass, newMetaClass);
    }

    static {
        setMetaClass(GroovySystem.getMetaClassRegistry().getMetaClass(GroovyNode.class), GroovyNode.class);
    }

}
