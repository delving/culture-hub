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

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Defines the root of a hierarchical model
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

@XStreamAlias("record-definition")
public class RecordDefinition {

    @XStreamAsAttribute
    public String prefix;

    public List<NamespaceDefinition> namespaces;

    public ElementDefinition root;

    void initialize() throws MetadataException {
        root.setPaths(new Path());
        root.setFactDefinitions();
    }

    public List<FieldDefinition> getMappableFields() {
        List<FieldDefinition> fields = new ArrayList<FieldDefinition>();
        root.getMappableFields(fields);
        return fields;
    }

    public FieldDefinition getFieldDefinition(Path path) {
        return root.getFieldDefinition(path);
    }

    public List<String> getFieldNameList() {
        List<String> fieldNames = new ArrayList<String>();
        root.getFieldNames(fieldNames);
        return fieldNames;
    }

    public Map<String, String> getFacetMap() {
        Map<String, String> facetMap = new TreeMap<String, String>();
        root.getFacetMap(facetMap);
        return facetMap;
    }

    public String[] getFacetFieldStrings() {
        List<String> facetFieldStrings = new ArrayList<String>();
        root.getFacetFieldStrings(facetFieldStrings);
        return facetFieldStrings.toArray(new String[facetFieldStrings.size()]);
    }

    public String[] getFieldStrings() {
        List<String> fieldStrings = new ArrayList<String>();
        root.getFieldStrings(fieldStrings);
        return fieldStrings.toArray(new String[fieldStrings.size()]);
    }

    public String toString() {
        return toString(this);
    }

    // handy static methods

    public static RecordDefinition read(InputStream in) throws MetadataException {
        try {
            Reader inReader = new InputStreamReader(in, "UTF-8");
            RecordDefinition recordDefinition = (RecordDefinition) stream().fromXML(inReader);
            recordDefinition.initialize();
            return recordDefinition;
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toString(RecordDefinition recordDefinition) {
        return stream().toXML(recordDefinition);
    }

    private static XStream stream() {
        XStream stream = new XStream(new PureJavaReflectionProvider());
        stream.processAnnotations(new Class[]{
                RecordDefinition.class,
                ElementDefinition.class,
                FieldDefinition.class
        });
        return stream;
    }

}
