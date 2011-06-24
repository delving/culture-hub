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

package eu.europeana.sip.model;

import eu.delving.metadata.FieldMapping;
import eu.delving.metadata.MappingModel;
import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.RecordMapping;
import eu.delving.metadata.RecordValidator;
import eu.europeana.sip.core.DiscardRecordException;
import eu.europeana.sip.core.GroovyCodeResource;
import eu.europeana.sip.core.MappingException;
import eu.europeana.sip.core.MappingRunner;
import eu.europeana.sip.core.MetadataRecord;
import eu.europeana.sip.core.RecordValidationException;
import org.apache.log4j.Logger;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This model is behind the scenario with input data, groovy code, and output record
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class CompileModel implements SipModel.ParseListener, MappingModel.Listener {
    private Logger log = Logger.getLogger(getClass());
    public final static int COMPILE_DELAY = 500;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private RecordMapping recordMapping;
    private MetadataRecord metadataRecord;
    private HTMLEditorKit editorKit = new HTMLEditorKit();
    private HTMLDocument inputDocument = (HTMLDocument) editorKit.createDefaultDocument();
    private Document codeDocument = new PlainDocument();
    private Document outputDocument = new PlainDocument();
    private CompileTimer compileTimer = new CompileTimer();
    private MetadataModel metadataModel;
    private Type type;
    private RecordValidator recordValidator;
    private String selectedPath;
    private String editedCode;
    private GroovyCodeResource groovyCodeResource;

    public enum Type {
        RECORD,
        FIELD
    }

    public enum State {
        UNCOMPILED,
        PRISTINE,
        EDITED,
        ERROR,
        COMMITTED,
        REGENERATED
    }

    public CompileModel(Type type, MetadataModel metadataModel, GroovyCodeResource groovyCodeResource) {
        this.type = type;
        this.metadataModel = metadataModel;
        this.groovyCodeResource = groovyCodeResource;
    }

    @Override
    public void mappingChanged(RecordMapping recordMapping) {
        if (this.recordMapping != recordMapping) {
            log.info("New record mapping, selected path eliminated");
            this.selectedPath = null;
        }
        this.recordMapping = recordMapping;
        this.editedCode = null;
        SwingUtilities.invokeLater(new DocumentSetter(codeDocument, getDisplayCode()));
        notifyStateChange(State.PRISTINE);
        compileSoon();
    }

    public void setSelectedPath(String selectedPath) {
        if (this.selectedPath != null && this.selectedPath.equals(selectedPath)) {
            log.info("Selected path unchanged at " + selectedPath);
            notifyStateChange(State.REGENERATED);
        }
        else {
            this.selectedPath = selectedPath;
            log.info("Selected path changed to " + selectedPath);
            notifyStateChange(State.PRISTINE);
        }
        SwingUtilities.invokeLater(new DocumentSetter(codeDocument, getDisplayCode()));
        compileSoon();
    }

    public FieldMapping getSelectedFieldMapping() {
        if (selectedPath == null) {
            return null;
        }
        return recordMapping.getFieldMapping(selectedPath);
    }

    public void setRecordValidator(RecordValidator recordValidator) {
        this.recordValidator = recordValidator;
    }

    public void refreshCode() {
        SwingUtilities.invokeLater(new DocumentSetter(codeDocument, getDisplayCode()));
        compileSoon();
    }

    public void compileSoon() {
        compileTimer.triggerSoon();
    }

    public void setCode(String code) {
        if (selectedPath != null) {
            FieldMapping fieldMapping = recordMapping.getFieldMapping(selectedPath);
            if (fieldMapping != null) {
                if (!fieldMapping.codeLooksLike(code)) {
                    editedCode = code;
                    log.info("Code looks different");
                    notifyStateChange(State.EDITED);
                }
                else {
                    editedCode = null;
                    log.info("Code looks the same");
                    notifyStateChange(State.PRISTINE);
                }
            }
            else {
                log.warn("Field mapping not found for " + selectedPath);
            }
            compileSoon();
        }
        else {
            log.info("setCode with no selected path");
        }
    }

    @Override
    public void updatedRecord(MetadataRecord metadataRecord) {
        this.metadataRecord = metadataRecord;
        if (metadataRecord == null) {
            SwingUtilities.invokeLater(new DocumentSetter(inputDocument, "<html><h1>No input</h1>"));
            SwingUtilities.invokeLater(new DocumentSetter(outputDocument, ""));
        }
        else {
            updateInputDocument(metadataRecord);
            compileSoon();
        }
    }

    public Document getInputDocument() {
        return inputDocument;
    }

    public Document getCodeDocument() {
        return codeDocument;
    }

    public Document getOutputDocument() {
        return outputDocument;
    }

    public String toString() {
        return type.toString();
    }

    // === privates

    private String getDisplayCode() {
        switch (type) {
            case RECORD:
                if (recordMapping != null) {
                    return recordMapping.toDisplayCode(metadataModel);
                }
                else {
                    return "// no mapping";
                }
            case FIELD:
                if (selectedPath == null) {
                    return "// no code";
                }
                else {
                    return recordMapping.toDisplayCode(metadataModel, selectedPath);
                }
            default:
                throw new RuntimeException();
        }
    }

    private String getCompileCode() {
        switch (type) {
            case RECORD:
                return recordMapping != null ? recordMapping.toCompileCode(metadataModel) : "";
            case FIELD:
                return selectedPath != null ? recordMapping.toCompileCode(metadataModel, selectedPath) : "";
            default:
                throw new RuntimeException();
        }
    }

    private String getCompileCode(String editedCode) {
        if (type == Type.RECORD) {
            throw new RuntimeException();
        }
        if (selectedPath == null) {
            return "print 'nothing selected'";
        }
        else {
            return recordMapping.toCompileCode(metadataModel, selectedPath, editedCode);
        }
    }

    private void updateInputDocument(MetadataRecord metadataRecord) {
        if (metadataRecord != null) {
            SwingUtilities.invokeLater(new DocumentSetter(inputDocument, metadataRecord.toHtml()));
        }
        else {
            SwingUtilities.invokeLater(new DocumentSetter(inputDocument, "<html><h1>No Input</h1>"));
        }
    }

    private class CompilationRunner implements Runnable {

        @Override
        public void run() {
            if (metadataRecord == null) {
                return;
            }
            String mappingCode;
            if (editedCode == null) {
                mappingCode = getCompileCode();
            }
            else {
                mappingCode = getCompileCode(editedCode);
                log.info("Edited code used");
            }
            MappingRunner mappingRunner = new MappingRunner(groovyCodeResource, mappingCode);
            try {
                try {
                    String output = mappingRunner.runMapping(metadataRecord);
                    if (recordValidator != null) {
                        List<String> problems = new ArrayList<String>();
                        String validated = recordValidator.validateRecord(output, problems);
                        if (problems.isEmpty()) {
                            compilationComplete(validated);
                        }
                        else {
                            throw new RecordValidationException(metadataRecord, problems);
                        }
                    }
                    else {
                        compilationComplete(output);
                        if (editedCode == null) {
                            notifyStateChange(State.PRISTINE);
                        }
                        else {
                            FieldMapping fieldMapping = recordMapping.getFieldMapping(selectedPath);
                            if (fieldMapping != null) {
                                fieldMapping.setCode(editedCode);
                                notifyStateChange(State.COMMITTED);
                                editedCode = null;
                                notifyStateChange(State.PRISTINE);
                            }
                            else {
                                notifyStateChange(State.EDITED);
                            }
                        }
                    }
                }
                catch (DiscardRecordException e) {
                    compilationComplete(e.getMessage());
                    FieldMapping fieldMapping = recordMapping.getFieldMapping(selectedPath);
                    if (fieldMapping != null) {
                        if (editedCode != null) {
                            fieldMapping.setCode(editedCode);
                            notifyStateChange(State.COMMITTED);
                            editedCode = null;
                        }
                        notifyStateChange(State.PRISTINE);
                    }
                    else {
                        notifyStateChange(State.EDITED);
                    }
                }
            }
            catch (MappingException e) {
                compilationComplete(e.getMessage());
                notifyStateChange(State.ERROR);
            }
            catch (RecordValidationException e) {
                compilationComplete(e.toString());
                notifyStateChange(State.ERROR);
            }
        }

        private void compilationComplete(final String result) {
            SwingUtilities.invokeLater(new DocumentSetter(outputDocument, result));
        }

        public String toString() {
            return type.toString();
        }
    }

    private class DocumentSetter implements Runnable {

        private Document document;
        private String content;

        private DocumentSetter(Document document, String content) {
            this.document = document;
            this.content = content;
        }

        @Override
        public void run() {
            if (document instanceof HTMLDocument) {
                HTMLDocument htmlDocument = (HTMLDocument) document;
                int docLength = document.getLength();
                try {
                    document.remove(0, docLength);
                    HTMLEditorKit.ParserCallback callback = htmlDocument.getReader(0);
                    htmlDocument.getParser().parse(new StringReader(content), callback, true);
                    callback.flush();
                }
                catch (BadLocationException e) {
                    throw new RuntimeException(e);
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            else {
                int docLength = document.getLength();
                try {
                    document.remove(0, docLength);
                    document.insertString(0, content, null);
                }
                catch (BadLocationException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private class CompileTimer implements ActionListener {
        private Timer timer = new Timer(COMPILE_DELAY, this);

        @Override
        public void actionPerformed(ActionEvent e) {
            timer.stop();
            executor.execute(new CompilationRunner());
        }

        public void triggerSoon() {
            timer.restart();
        }
    }

    private void notifyStateChange(State state) {
        for (Listener listener : listeners) {
            listener.stateChanged(state);
        }
    }

    public interface Listener {
        void stateChanged(State state);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    private List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
}