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

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Wrap an output stream so it stores a source file full of records
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class SourceStream {
    public static final String ENVELOPE_TAG = "delving-sip-source";
    public static final String RECORD_TAG = "record";
    private XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
    private XMLEventFactory eventFactory = XMLEventFactory.newInstance();
    private OutputStream outputStream;
    private Hasher hasher = new Hasher();
    private GZIPOutputStream gzipOutputStream;
    private Writer recordWriter;
    private XMLEventWriter xmlEventWriter;

    public SourceStream(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    public void startZipStream(Map map) throws XMLStreamException, IOException {
        gzipOutputStream = new GZIPOutputStream(outputStream);
        recordWriter = new OutputStreamWriter(hasher.createDigestOutputStream(gzipOutputStream), "UTF-8");
        recordWriter.write("<?xml version=\"1.0\"?>\n");
        recordWriter.write(String.format("<%s\n", ENVELOPE_TAG));
        for (Object entryObject : map.entrySet()) {
            Map.Entry entry = (Map.Entry)entryObject;
            recordWriter.write(String.format("   xmlns:%s=\"%s\"\n", entry.getKey(), entry.getValue()));
        }
        recordWriter.write(">");
    }


    public static void adjustPathsForHarvest(Facts facts) throws MetadataException {
        String recordRootPath = String.format("/%s/%s/metadata/record", SourceStream.ENVELOPE_TAG, SourceStream.RECORD_TAG);
        if (!facts.getRecordRootPath().equals(recordRootPath)) {
            String relativeUniquePath = facts.getRelativeUniquePath();
            facts.setRecordRootPath(recordRootPath);
            facts.setUniqueElementPath(recordRootPath + relativeUniquePath);
        }
    }

    public void addRecord(String record) throws XMLStreamException, IOException {
        recordWriter.write(String.format("<%s>\n", RECORD_TAG));
        recordWriter.write(record);
        recordWriter.write(String.format("</%s>\n", RECORD_TAG));
    }

    public String endZipStream() throws XMLStreamException, IOException {
        recordWriter.write(String.format("</%s>\n", ENVELOPE_TAG));
        recordWriter.flush();
        gzipOutputStream.finish();
        return hasher.getHashString();
    }

    public void startEventStream() throws UnsupportedEncodingException, XMLStreamException {
        xmlEventWriter = outputFactory.createXMLEventWriter(new OutputStreamWriter(outputStream, "UTF-8"));
        xmlEventWriter.add(eventFactory.createStartDocument());
        xmlEventWriter.add(eventFactory.createStartElement("", "", ENVELOPE_TAG));
    }

    public void addEvent(XMLEvent event) throws XMLStreamException {
        xmlEventWriter.add(event);
    }

    public void endEventStream() throws XMLStreamException {
        xmlEventWriter.add(eventFactory.createEndElement("", "", "harvest"));
        xmlEventWriter.add(eventFactory.createEndDocument());
        xmlEventWriter.flush();
    }

}
