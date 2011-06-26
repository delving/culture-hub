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

import groovy.util.BuilderSupport;
import groovy.xml.QName;

import java.util.Map;

/**
 * Build a tree of GroovyNode instances using the builder pattern, as an alternative to using
 * the MarkupBuilder
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

@SuppressWarnings("unchecked")
public class GroovyBuilder extends BuilderSupport {

    protected void setParent(Object parent, Object child) {
    }

    protected Object createNode(Object name) {
        return new GroovyNode(getCurrentNode(), (QName)name, new GroovyList());
    }

    protected Object createNode(Object name, Object value) {
        return new GroovyNode(getCurrentNode(), (QName)name, value.toString());
    }

    protected Object createNode(Object name, Map attributes) {
        return new GroovyNode(getCurrentNode(), (QName)name, attributes, new GroovyList());
    }

    protected Object createNode(Object name, Map attributes, Object value) {
        return new GroovyNode(getCurrentNode(), (QName)name, attributes, value.toString());
    }

    protected GroovyNode getCurrentNode() {
        return (GroovyNode) getCurrent();
    }
}

