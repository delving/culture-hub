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

import eu.delving.metadata.MetadataNamespace;
import eu.delving.metadata.RecordMapping;
import eu.delving.metadata.RecordValidator;
import eu.delving.metadata.Uniqueness;
import eu.delving.sip.FileStore;
import eu.delving.sip.FileStoreException;
import eu.delving.sip.ProgressListener;
import eu.europeana.sip.core.DiscardRecordException;
import eu.europeana.sip.core.GroovyCodeResource;
import eu.europeana.sip.core.MappingException;
import eu.europeana.sip.core.MappingRunner;
import eu.europeana.sip.core.MetadataRecord;
import eu.europeana.sip.core.RecordValidationException;
import eu.europeana.sip.model.SipModel;
import org.apache.log4j.Logger;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Take the input and config informationm and produce an output xml file
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class Normalizer implements Runnable {
    private Logger log = Logger.getLogger(getClass());
    private SipModel sipModel;
    private boolean discardInvalid;
    private File normalizeDirectory;
    private GroovyCodeResource groovyCodeResource;
    private ProgressAdapter progressAdapter;
    private Listener listener;
    private volatile boolean running = true;
    private long totalMappingTime, totalValidationTime;

    public interface Listener {
        void invalidInput(MappingException exception);

        void invalidOutput(RecordValidationException exception);

        void finished(boolean success);
    }

    public Normalizer(
            SipModel sipModel,
            boolean discardInvalid,
            File normalizeDirectory,
            GroovyCodeResource groovyCodeResource,
            ProgressListener progressListener,
            Listener listener
    ) {
        this.sipModel = sipModel;
        this.discardInvalid = discardInvalid;
        this.normalizeDirectory = normalizeDirectory;
        this.groovyCodeResource = groovyCodeResource;
        this.progressAdapter = new ProgressAdapter(progressListener);
        this.listener = listener;
    }

    public void run() {
        FileStore.MappingOutput fileSetOutput = null;
        boolean store = normalizeDirectory != null;
        Uniqueness uniqueness = new Uniqueness();
        RecordValidator recordValidator = new RecordValidator(sipModel.getRecordDefinition());
        recordValidator.guardUniqueness(uniqueness);
        try {
            RecordMapping recordMapping = sipModel.getMappingModel().getRecordMapping();
            if (recordMapping == null) {
                return;
            }
            fileSetOutput = sipModel.getDataSetStore().createMappingOutput(recordMapping, normalizeDirectory);
            if (store) {
                Writer out = fileSetOutput.getOutputWriter();
                out.write("<?xml version='1.0' encoding='UTF-8'?>\n");
                out.write("<metadata");
                writeNamespace(out, MetadataNamespace.DC);
                writeNamespace(out, MetadataNamespace.DCTERMS);
                writeNamespace(out, MetadataNamespace.EUROPEANA);
                out.write(">\n");
            }
            MappingRunner mappingRunner = new MappingRunner(
                    groovyCodeResource,
                    recordMapping.toCompileCode(sipModel.getMetadataModel())
            );
            MetadataParser parser = new MetadataParser(
                    sipModel.getDataSetStore().createXmlInputStream(),
                    sipModel.getRecordRoot(),
                    sipModel.getRecordCount()
            );
            parser.setProgressListener(progressAdapter);
            MetadataRecord record;
            while ((record = parser.nextRecord()) != null && running) {
                try {
                    long before = System.currentTimeMillis();
                    String output = mappingRunner.runMapping(record);
                    totalMappingTime += System.currentTimeMillis() - before;
                    List<String> problems = new ArrayList<String>();
                    before = System.currentTimeMillis();
                    String validated = recordValidator.validateRecord(output, problems);
                    totalValidationTime += System.currentTimeMillis() - before;
                    if (problems.isEmpty()) {
                        if (store) {
                            fileSetOutput.getOutputWriter().write(validated);
                        }
                        fileSetOutput.recordNormalized();
                    }
                    else {
                        throw new RecordValidationException(record, problems);
                    }
                }
                catch (MappingException e) {
                    if (discardInvalid) {
                        if (store) {
                            try {
                                fileSetOutput.getDiscardedWriter().write(record.toString());
                                e.printStackTrace(new PrintWriter(fileSetOutput.getDiscardedWriter()));
                                fileSetOutput.getDiscardedWriter().write("\n========================================\n");
                            }
                            catch (IOException e1) {
                                sipModel.getUserNotifier().tellUser("Unable to write discarded record", e1);
                                abort();
                            }
                        }
                        fileSetOutput.recordDiscarded();
                    }
                    else {
                        listener.invalidInput(e);
                        abort();
                    }
                }
                catch (RecordValidationException e) {
                    if (discardInvalid) {
                        if (store) {
                            try {
                                fileSetOutput.getDiscardedWriter().write(record.toString());
                                e.printStackTrace(new PrintWriter(fileSetOutput.getDiscardedWriter()));
                                fileSetOutput.getDiscardedWriter().write("\n========================================\n");
                            }
                            catch (IOException e1) {
                                sipModel.getUserNotifier().tellUser("Unable to write discarded record", e1);
                                abort();
                            }
                        }
                        fileSetOutput.recordDiscarded();
                    }
                    else {
                        listener.invalidOutput(e);
                        abort();
                    }
                }
                catch (DiscardRecordException e) {
                    if (store) {
                        try {
                            fileSetOutput.getDiscardedWriter().write("Discarded explicitly: \n" + record.toString());
                            fileSetOutput.getDiscardedWriter().write("\n========================================\n");
                        }
                        catch (IOException e1) {
                            sipModel.getUserNotifier().tellUser("Unable to write discarded record", e1);
                            abort();
                        }
                    }
                    fileSetOutput.recordDiscarded();
                }
                catch (Exception e) {
                    sipModel.getUserNotifier().tellUser("Problem writing output", e);
                    abort();
                }
            }
            if (store) {
                fileSetOutput.getOutputWriter().write("</metadata>\n");
            }
            Set<String> repeated = uniqueness.getRepeated();
            if (!repeated.isEmpty()) {
                StringBuilder out = new StringBuilder();
                int countdown = 6;
                for (String line : repeated) {
                    if (countdown-- == 0) break;
                    out.append(line).append("<br>");
                }
                sipModel.getUserNotifier().tellUser(String.format("<html>Identifier should be unique, but there were %d repeats, including:<br> ", repeated.size())+out);
            }
            fileSetOutput.close(!running);
        }
        catch (XMLStreamException e) {
            throw new RuntimeException("XML Problem", e);
        }
        catch (IOException e) {
            throw new RuntimeException("IO Problem", e);
        }
        catch (FileStoreException e) {
            throw new RuntimeException("Datastore Problem", e);
        }
        catch (MetadataParser.AbortException e) {
            if (fileSetOutput != null) {
                try {
                    fileSetOutput.close(true);
                }
                catch (FileStoreException e1) {
                    throw new RuntimeException("Couldn't close output properly");
                }
            }
        }
        finally {
            log.info(String.format("Mapping Time %d", totalMappingTime));
            log.info(String.format("Validating Time %d", totalValidationTime));
            recordValidator.report();
            listener.finished(running);
            uniqueness.destroy();
            if (!running) { // aborted, so metadataparser will not call finished()
                progressAdapter.finished(false);
            }
        }
    }

    private void abort() {
        running = false;
    }

    private void writeNamespace(Writer writer, MetadataNamespace namespace) throws IOException {
        writer.write(String.format(" xmlns:%s=\"%s\"", namespace.getPrefix(), namespace.getUri()));
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
