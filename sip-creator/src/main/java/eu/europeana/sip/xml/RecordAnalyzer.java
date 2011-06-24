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

package eu.europeana.sip.xml;

import eu.delving.sip.FileStoreException;
import eu.delving.sip.ProgressListener;
import eu.europeana.sip.core.MetadataRecord;
import eu.europeana.sip.model.SipModel;
import groovy.lang.GroovyClassLoader;
import groovy.xml.MarkupBuilder;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Analyze records to gather some statistics
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class RecordAnalyzer implements Runnable {
    private SipModel sipModel;
    private ProgressAdapter progressAdapter;
    private Listener listener;
    private Object groovyInstance;
    private Method consumeRecordMethod;
    private Method produceHtmlMethod;
    private volatile boolean running = true;
    private String recordAnalysisCode;
    private int recordCountLimit;

    public interface Listener {
        void finished(String html);
    }

    public RecordAnalyzer(
            SipModel sipModel,
            String recordAnalysisCode,
            int recordCountLimit,
            ProgressListener progressListener,
            Listener listener
    ) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InstantiationException {
        this.sipModel = sipModel;
        this.recordAnalysisCode = recordAnalysisCode;
        this.recordCountLimit = recordCountLimit;
        this.progressAdapter = new ProgressAdapter(progressListener);
        this.listener = listener;
        setupGroovyClass();
    }

    private void setupGroovyClass() throws NoSuchMethodException, InstantiationException, IllegalAccessException {
        Class groovyClass = new GroovyClassLoader(getClass().getClassLoader()).parseClass(recordAnalysisCode);
        this.consumeRecordMethod = groovyClass.getMethod("consumeRecord", Object.class);
        this.produceHtmlMethod = groovyClass.getMethod("produceHtml", Object.class);
        this.groovyInstance = groovyClass.newInstance();
    }

    public void run() {
        try {
            MetadataParser parser = new MetadataParser(
                    sipModel.getDataSetStore().createXmlInputStream(),
                    sipModel.getRecordRoot(),
                    sipModel.getRecordCount()
            );
            parser.setProgressListener(progressAdapter);
            MetadataRecord record;
            while ((record = parser.nextRecord()) != null && running) {
                if (recordCountLimit > 0 && record.getRecordNumber() > recordCountLimit) {
                    parser.close();
                    break;
                }
                consumeRecordMethod.invoke(groovyInstance, record.getRootNode());
            }
            progressAdapter.finished(true);
        }
        catch (XMLStreamException e) {
            abort();
            sipModel.getUserNotifier().tellUser("XML Problem", e);
        }
        catch (IOException e) {
            abort();
            sipModel.getUserNotifier().tellUser("IO Problem", e);
        }
        catch (FileStoreException e) {
            abort();
            sipModel.getUserNotifier().tellUser("Datastore Problem", e);
        }
        catch (MetadataParser.AbortException e) {
            abort();
            sipModel.getUserNotifier().tellUser("Aborted", e);
        }
        catch (IllegalAccessException e) {
            abort();
            sipModel.getUserNotifier().tellUser("Class Problem", e);
        }
        catch (InvocationTargetException e) {
            abort();
            sipModel.getUserNotifier().tellUser("Class Problem", e);
        }
        finally {
            if (!running) { // aborted, so metadataparser will not call finished()
                listener.finished("<html><h1>Analysis Aborted</h1>");
            }
            else {
                StringWriter html = new StringWriter();
                MarkupBuilder markup = new MarkupBuilder(html);
                try {
                    produceHtmlMethod.invoke(groovyInstance, markup);
                }
                catch (Exception e) {
                    sipModel.getUserNotifier().tellUser("Problem producing HTML", e);
                }
                listener.finished(html.toString());
            }
        }
    }

    private void abort() {
        progressAdapter.finished(false);
        running = false;
    }

    // just so we receive the cancel signal

    private class ProgressAdapter implements ProgressListener {
        private ProgressListener progressListener;

        private ProgressAdapter(ProgressListener progressListener) {
            this.progressListener = progressListener;
        }

        @Override
        public void setTotal(int total) {
            progressListener.setTotal(total);
        }

        @Override
        public boolean setProgress(int progress) {
            boolean proceed = progressListener.setProgress(progress);
            if (!proceed) {
                running = false;
            }
            return running && proceed;
        }

        @Override
        public void finished(boolean success) {
            progressListener.finished(success);
        }
    }
}
