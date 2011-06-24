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

package eu.europeana.sip.xml;

import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;
import eu.delving.sip.FileStore;
import org.apache.log4j.Logger;
import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.XMLEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analyze xml input and compile statistics.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */

public class AnalysisParser implements Runnable {
    private static final int ELEMENT_STEP = 10000;
    private final Logger LOG = Logger.getLogger(getClass());
    private Path path = new Path();
    private Map<Path, FieldStatistics> statisticsMap = new HashMap<Path, FieldStatistics>();
    private Listener listener;
    private FileStore.DataSetStore dataSetStore;
    private boolean abort;

    public interface Listener {

        void success(List<FieldStatistics> list);

        void failure(Exception exception);

        void progress(long elementCount);
    }

    public AnalysisParser(FileStore.DataSetStore dataSetStore, Listener listener) {
        this.dataSetStore = dataSetStore;
        this.listener = listener;
    }

    public void abort() {
        abort = true;
    }

    @Override
    public void run() {
        try {
            XMLInputFactory2 xmlif = (XMLInputFactory2) XMLInputFactory2.newInstance();
            xmlif.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);
            xmlif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            xmlif.setProperty(XMLInputFactory.IS_COALESCING, Boolean.FALSE);
            xmlif.configureForSpeed();
            XMLStreamReader2 input = (XMLStreamReader2) xmlif.createXMLStreamReader(getClass().getName(), dataSetStore.createXmlInputStream());
            StringBuilder text = new StringBuilder();
            long count = 0;
            while (!abort) {
                switch (input.getEventType()) {
                    case XMLEvent.START_DOCUMENT:
                        LOG.info("Starting document");
                        break;
                    case XMLEvent.START_ELEMENT:
                        if (++count % ELEMENT_STEP == 0) {
                            if (null != listener) {
                                listener.progress(count);
                            }
                        }
                        path.push(Tag.create(input.getName().getPrefix(),input.getName().getLocalPart()));
//                        if (input.getAttributeCount() > 0) {
//                            for (int walk = 0; walk < input.getAttributeCount(); walk++) {
//                                QName attributeName = input.getAttributeName(walk);
//                                path.push(Tag.create(attributeName.getPrefix(), attributeName.getLocalPart()));
//                                recordValue(input.getAttributeValue(walk));
//                                path.pop();
//                            }
//                        }
                        break;
                    case XMLEvent.CHARACTERS:
                        text.append(input.getText());
                        break;
                    case XMLEvent.CDATA:
                        text.append(input.getText());
                        break;
                    case XMLEvent.END_ELEMENT:
                        recordValue(text.toString());
                        text.setLength(0);
                        path.pop();
                        break;
                    case XMLEvent.END_DOCUMENT: {
                        LOG.info("Ending document");
                        break;
                    }
                }
                if (!input.hasNext()) {
                    break;
                }
                input.next();
            }
            List<FieldStatistics> fieldStatisticsList = new ArrayList<FieldStatistics>(statisticsMap.values());
            Collections.sort(fieldStatisticsList);
            for (FieldStatistics fieldStatistics : fieldStatisticsList) {
                fieldStatistics.finish();
            }
            dataSetStore.setStatistics(fieldStatisticsList);
            listener.success(fieldStatisticsList);
        }
        catch (Exception e) {
            LOG.error("Analysis Failed!", e);
            listener.failure(e);
        }
    }

    private void recordValue(String value) {
        value = value.trim();
        FieldStatistics fieldStatistics = statisticsMap.get(path);
        if (fieldStatistics == null) {
            Path key = new Path(path);
            statisticsMap.put(key, fieldStatistics = new FieldStatistics(key));
        }
        if (!value.isEmpty()) {
            fieldStatistics.recordValue(value);
        }
        fieldStatistics.recordOccurrence();
    }
}