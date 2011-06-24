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

/**
 * Gather namespace definitions together
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public enum MetadataNamespace {
    RAW(
            "raw",
            "http://delving.eu/namespaces/raw",
            "http://delving.eu/namespaces/raw/schema.xsd"
    ),
    DC(
            "dc",
            "http://purl.org/dc/elements/1.1/",
            "http://dublincore.org/schemas/xmls/qdc/dc.xsd"
    ),
    DCTERMS (
            "dcterms",
            "http://purl.org/dc/terms/",
            "http://dublincore.org/schemas/xmls/qdc/dcterms.xsd"
    ),
    EUROPEANA(
            "europeana",
            "http://www.europeana.eu/schemas/ese/",
            "http://www.europeana.eu/schemas/ese/ESE-V3.3.xsd"
    ),
    ESE(
            "ese",
            "http://www.europeana.eu/schemas/ese/",
            "http://www.europeana.eu/schemas/ese/ESE-V3.3.xsd"
    ),
    ICN(
            "icn",
            "http://www.icn.nl/",
            "http://www.icn.nl/schemas/ICN-V3.2.xsd"
    ),
    ABM(
            "abm",
            "http://abmu.org/abm",
            "http://abmu.org/abm.xsd"
    );

    MetadataNamespace(String prefix, String uri, String schema) {
        this.prefix = prefix;
        this.uri = uri;
        this.schema = schema;
    }

    private String prefix;
    private String uri;
    private String schema;

    public String getPrefix() {
        return prefix;
    }

    public String getUri() {
        return uri;
    }

    public String getSchema() {
        return schema;
    }
}
