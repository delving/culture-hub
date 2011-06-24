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

import eu.delving.metadata.AnalysisTree;
import eu.delving.metadata.Facts;
import eu.delving.metadata.FieldDefinition;
import eu.delving.metadata.FieldMapping;
import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.MappingModel;
import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecordDefinition;
import eu.delving.metadata.RecordMapping;
import eu.delving.metadata.RecordValidator;
import eu.delving.metadata.SourceVariable;
import eu.delving.sip.AppConfig;
import eu.delving.sip.FileStore;
import eu.delving.sip.FileStoreException;
import eu.delving.sip.ProgressListener;
import eu.europeana.sip.core.GroovyCodeResource;
import eu.europeana.sip.core.MappingException;
import eu.europeana.sip.core.MetadataRecord;
import eu.europeana.sip.core.RecordValidationException;
import eu.europeana.sip.xml.AnalysisParser;
import eu.europeana.sip.xml.MetadataParser;
import eu.europeana.sip.xml.Normalizer;
import eu.europeana.sip.xml.RecordAnalyzer;
import org.apache.log4j.Logger;

import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * This model is behind the whole sip creator, as a facade for all the models related to a data set
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class SipModel {
    private Logger log = Logger.getLogger(getClass());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private FileStore fileStore;
    private MetadataModel metadataModel;
    private GroovyCodeResource groovyCodeResource;
    private AppConfigModel appConfigModel;
    private FileStore.DataSetStore dataSetStore;
    private Facts facts;
    private UserNotifier userNotifier;
    private List<FieldStatistics> fieldStatisticsList;
    private AnalysisTree analysisTree;
    private DefaultTreeModel analysisTreeModel;
    private FieldListModel fieldListModel;
    private CompileModel recordCompileModel;
    private CompileModel fieldCompileModel;
    private MetadataParser metadataParser;
    private MetadataRecord metadataRecord;
    private FactModel factModel = new FactModel();
    private FieldMappingListModel fieldMappingListModel;
    private MappingModel mappingModel = new MappingModel();
    private MappingSaveTimer mappingSaveTimer = new MappingSaveTimer();
    private VariableListModel variableListModel = new VariableListModel();
    private List<UpdateListener> updateListeners = new CopyOnWriteArrayList<UpdateListener>();
    private List<ParseListener> parseListeners = new CopyOnWriteArrayList<ParseListener>();

    public interface UpdateListener {

        void updatedDataSetStore(FileStore.DataSetStore dataSetStore);

        void updatedStatistics(FieldStatistics fieldStatistics);

        void updatedRecordRoot(Path recordRoot, int recordCount);

        void normalizationMessage(boolean complete, String message);
    }

    public interface AnalysisListener {
        void finished(boolean success);

        void analysisProgress(long elementCount);
    }

    public interface ParseListener {
        void updatedRecord(MetadataRecord metadataRecord);
    }

    public interface ScanPredicate {
        boolean accept(MetadataRecord record);
    }

    public SipModel(FileStore fileStore, MetadataModel metadataModel, GroovyCodeResource groovyCodeResource, UserNotifier userNotifier) throws FileStoreException {
        this.fileStore = fileStore;
        this.appConfigModel = new AppConfigModel(fileStore.getAppConfig());
        this.appConfigModel.addListener(new AppConfigModel.Listener() {
            @Override
            public void appConfigUpdated(AppConfig appConfig) {
                executor.execute(new AppConfigSetter(appConfig));
            }
        });
        this.metadataModel = metadataModel;
        this.groovyCodeResource = groovyCodeResource;
        this.userNotifier = userNotifier;
        analysisTree = AnalysisTree.create("Select a Data Set from the File menu");
        analysisTreeModel = new DefaultTreeModel(analysisTree.getRoot());
        fieldListModel = new FieldListModel(metadataModel);
        recordCompileModel = new CompileModel(CompileModel.Type.RECORD, metadataModel, groovyCodeResource);
        fieldCompileModel = new CompileModel(CompileModel.Type.FIELD, metadataModel, groovyCodeResource);
        parseListeners.add(recordCompileModel);
        parseListeners.add(fieldCompileModel);
        fieldMappingListModel = new FieldMappingListModel();
        factModel.addListener(new FactModelAdapter());
        mappingModel.addListener(fieldMappingListModel);
        mappingModel.addListener(recordCompileModel);
        mappingModel.addListener(fieldCompileModel);
        mappingModel.addListener(mappingSaveTimer);
        fieldCompileModel.addListener(new CompileModel.Listener() {
            @Override
            public void stateChanged(CompileModel.State state) {
                switch (state) {
                    case COMMITTED:
                    case REGENERATED:
                        mappingSaveTimer.mappingChanged(null);
                }
            }
        });
    }

    public void addUpdateListener(UpdateListener updateListener) {
        updateListeners.add(updateListener);
    }

    public void addParseListener(ParseListener parseListener) {
        parseListeners.add(parseListener);
    }

    public FileStore getFileStore() {
        return fileStore;
    }

    public void createDataSetStore(final FileStore.DataSetStore dataSetStore, final File file, final ProgressListener progressListener) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    dataSetStore.importFile(file, progressListener);
                }
                catch (FileStoreException e) {
                    userNotifier.tellUser("Couldn't create Data Set from " + file.getAbsolutePath(), e);
                }
            }
        });
    }

    public FactModel getFactModel() {
        return factModel;
    }

    public FileStore.DataSetStore getDataSetStore() {
        return dataSetStore;
    }

    public AppConfigModel getAppConfigModel() {
        return appConfigModel;
    }

    public MetadataModel getMetadataModel() {
        return metadataModel;
    }

    public MappingModel getMappingModel() {
        return mappingModel;
    }

    public UserNotifier getUserNotifier() {
        return userNotifier;
    }

    public void setDataSetStore(final FileStore.DataSetStore dataSetStore) {
        checkSwingThread();
        this.dataSetStore = dataSetStore;
        if (dataSetStore != null) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    final List<FieldStatistics> statistics = dataSetStore.getStatistics();
                    final Facts facts = dataSetStore.getFacts();
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            SipModel.this.facts = facts;
                            factModel.clear();
                            factModel.setFacts(facts, dataSetStore.getSpec());
                            mappingModel.setRecordMapping(null);
                            setStatisticsList(statistics);
                            variableListModel.clear();
                            AnalysisTree.setUniqueElement(analysisTreeModel, getUniqueElement());
                            for (UpdateListener updateListener : updateListeners) {
                                updateListener.updatedDataSetStore(dataSetStore);
                            }
                        }
                    });
                }
            });
        }
        else {
            for (UpdateListener updateListener : updateListeners) {
                updateListener.updatedDataSetStore(this.dataSetStore);
            }
        }
    }

    public void setMetadataPrefix(final String metadataPrefix) {
        checkSwingThread();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final RecordMapping recordMapping = dataSetStore.getRecordMapping(metadataPrefix);
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            mappingModel.setRecordMapping(recordMapping);
                            recordCompileModel.setRecordValidator(new RecordValidator(getRecordDefinition()));
                            seekFirstRecord();
                            if (recordMapping != null) {
                                if (getRecordRoot() != null) {
                                    setRecordRootInternal(new Path(facts.getRecordRootPath()), Integer.parseInt(facts.getRecordCount()));
                                }
                                if (recordMapping.getNormalizeTime() == 0) {
                                    normalizeMessage(false, "Normalization not yet performed.");
                                }
                                else {
                                    normalizeMessage(recordMapping);
                                }
                            }
                        }
                    });
                }
                catch (FileStoreException e) {
                    userNotifier.tellUser("Unable to select Metadata Prefix " + metadataPrefix, e);
                }
            }
        });
    }

    public RecordDefinition getRecordDefinition() {
        RecordMapping recordMapping = mappingModel.getRecordMapping();
        if (recordMapping == null) {
            return null;
        }
        return metadataModel.getRecordDefinition(recordMapping.getPrefix());
    }

    public void saveAsTemplate(final String name) {
        try {
            fileStore.setTemplate(name, mappingModel.getRecordMapping());
        }
        catch (FileStoreException e) {
            userNotifier.tellUser("Unable to store template", e);
        }
    }

    public void applyTemplate(RecordMapping template) {
        if (!mappingModel.getRecordMapping().getFieldMappings().isEmpty()) {
            userNotifier.tellUser("Record must be empty to use a template.");
        }
        else {
            try {
                template.apply(getRecordDefinition());
                mappingModel.applyTemplate(template);
                seekFirstRecord();
            }
            catch (Exception e) {
                userNotifier.tellUser("Unable to load template", e);
            }
        }
    }

    public void analyzeRecords(int recordCount, ProgressListener progressListener, RecordAnalyzer.Listener recordAnalyzerListener) {
        try {
            executor.execute(new RecordAnalyzer(
                    this,
                    fileStore.getCode(FileStore.RECORD_ANALYSIS_FILE_NAME),
                    recordCount,
                    progressListener,
                    recordAnalyzerListener
            ));
        }
        catch (Exception e) {
            userNotifier.tellUser("Unable to start Record Analyzer", e);
        }
    }

    public void analyzeFields(final AnalysisListener listener) {
        checkSwingThread();
        executor.execute(new AnalysisParser(dataSetStore, new AnalysisParser.Listener() {

            @Override
            public void success(final List<FieldStatistics> list) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        setStatisticsList(list);
                    }
                });
                listener.finished(true);
            }

            @Override
            public void failure(Exception exception) {
                listener.finished(false);
                userNotifier.tellUser("Analysis failed", exception);
            }

            @Override
            public void progress(long elementCount) {
                listener.analysisProgress(elementCount);
            }
        }));
    }

    public Facts getFacts() {
        return facts;
    }

    public void normalize(File normalizeDirectory, boolean discardInvalid, final ProgressListener progressListener) {
        checkSwingThread();
        normalizeMessage(false, "Normalizing and validating...");
        executor.execute(new Normalizer(
                this,
                discardInvalid,
                normalizeDirectory,
                groovyCodeResource,
                progressListener,
                new Normalizer.Listener() {
                    @Override
                    public void invalidInput(final MappingException exception) {
                        userNotifier.tellUser("Problem normalizing " + exception.getMetadataRecord().toString(), exception);
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                seekRecordNumber(exception.getMetadataRecord().getRecordNumber(), progressListener);
                            }
                        });
                    }

                    @Override
                    public void invalidOutput(final RecordValidationException exception) {
                        userNotifier.tellUser("Invalid output record", exception);
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                seekRecordNumber(exception.getMetadataRecord().getRecordNumber(), progressListener);
                            }
                        });
                    }

                    @Override
                    public void finished(final boolean success) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                if (success) {
                                    normalizeMessage(getMappingModel().getRecordMapping());
                                }
                                else {
                                    normalizeMessage(false, "Normalization aborted");
                                }
                            }
                        });
                    }
                }
        ));
    }

    public TreeModel getAnalysisTreeModel() {
        return analysisTreeModel;
    }

    public Path getUniqueElement() {
        if (facts == null || facts.getUniqueElementPath().isEmpty()) {
            return null;
        }
        return new Path(getFacts().getUniqueElementPath());
    }

    public void setUniqueElement(Path uniqueElement) {
        facts.setUniqueElementPath(uniqueElement.toString());
        factModel.setFacts(facts, dataSetStore.getSpec());
        executor.execute(new FactsSetter(facts));
        AnalysisTree.setUniqueElement(analysisTreeModel, uniqueElement);
    }

    public Path getRecordRoot() {
        if (facts == null || facts.getRecordRootPath().isEmpty()) {
            return null;
        }
        return new Path(getFacts().getRecordRootPath());
    }

    public int getRecordCount() {
        if (facts == null || facts.getRecordCount().isEmpty()) {
            return 0;
        }
        return Integer.parseInt(getFacts().getRecordCount());
    }

    public void setRecordRoot(Path recordRoot, int recordCount) {
        checkSwingThread();
        setRecordRootInternal(recordRoot, recordCount);
        seekFirstRecord();
        facts.setRecordRootPath(recordRoot.toString());
        facts.setRecordCount(String.valueOf(recordCount));
        factModel.setFacts(facts, dataSetStore.getSpec());
        executor.execute(new FactsSetter(facts));
    }

    public long getElementCount() {
        if (fieldStatisticsList != null) {
            long total = 0L;
            for (FieldStatistics stats : fieldStatisticsList) {
                total += stats.getTotal();
            }
            return total;
        }
        else {
            return 0L;
        }
    }

    public ListModel getUnmappedFieldListModel() {
        return fieldListModel.getUnmapped(getMappingModel());
    }

    public List<FieldDefinition> getUnmappedFields() {
        List<FieldDefinition> fields = new ArrayList<FieldDefinition>();
        ListModel listModel = getUnmappedFieldListModel();
        for (int walkField = 0; walkField < listModel.getSize(); walkField++) {
            fields.add((FieldDefinition) listModel.getElementAt(walkField));
        }
        return fields;
    }

    public ListModel getVariablesListModel() {
        return variableListModel;
    }

    public List<SourceVariable> getVariables() {
        List<SourceVariable> list = new ArrayList<SourceVariable>();
        for (int walkVar = 0; walkVar < variableListModel.getSize(); walkVar++) {
            list.add((SourceVariable) variableListModel.getElementAt(walkVar));
        }
        return list;
    }

    public ListModel getVariablesListWithCountsModel() {
        return variableListModel.getWithCounts(getMappingModel());
    }

    public void addFieldMapping(FieldMapping fieldMapping) {
        checkSwingThread();
        getMappingModel().setMapping(fieldMapping.getDefinition().path.toString(), fieldMapping);
    }

    public void removeFieldMapping(FieldMapping fieldMapping) {
        checkSwingThread();
        getMappingModel().setMapping(fieldMapping.getDefinition().path.toString(), null);
    }

    public ListModel getFieldMappingListModel() {
        return fieldMappingListModel;
    }

    public void seekFresh() {
        if (metadataParser != null) {
            metadataParser.close();
            metadataParser = null;
        }
    }

    public void seekFirstRecord() {
        seekRecordNumber(0, null);
    }

    public void seekRecordNumber(final int recordNumber, ProgressListener progressListener) {
        seekFresh();
        seekRecord(
                new ScanPredicate() {
                    @Override
                    public boolean accept(MetadataRecord record) {
                        return record.getRecordNumber() == recordNumber;
                    }
                },
                progressListener
        );
    }

    public void seekRecord(ScanPredicate scanPredicate, ProgressListener progressListener) {
        checkSwingThread();
        if (getRecordRoot() != null) {
            executor.execute(new RecordScanner(scanPredicate, progressListener));
        }
    }

    public CompileModel getRecordCompileModel() {
        return recordCompileModel;
    }

    public CompileModel getFieldCompileModel() {
        return fieldCompileModel;
    }

    // === privates

    private void normalizeMessage(boolean complete, String message) {
        for (UpdateListener updateListener : updateListeners) {
            updateListener.normalizationMessage(complete, message);
        }
    }

    private void normalizeMessage(RecordMapping recordMapping) {
        Date date = new Date(recordMapping.getNormalizeTime());
        String message = String.format(
                "<html>Completed at %tT on %tY-%tm-%td<br>with %d normalized, and %d discarded",
                date, date, date, date,
                recordMapping.getRecordsNormalized(),
                recordMapping.getRecordsDiscarded()
        );
        normalizeMessage(true, message);
    }

    private void setRecordRootInternal(Path recordRoot, int recordCount) {
        checkSwingThread();
        List<AnalysisTree.Node> variables = new ArrayList<AnalysisTree.Node>();
        if (recordRoot != null) {
            AnalysisTree.setRecordRoot(analysisTreeModel, recordRoot);
            analysisTree.getVariables(variables);
            variableListModel.setVariableList(variables);
        }
        else {
            variableListModel.clear();
        }
        for (UpdateListener updateListener : updateListeners) {
            updateListener.updatedRecordRoot(recordRoot, recordCount);
        }
    }

    public void setStatistics(FieldStatistics fieldStatistics) {
        for (UpdateListener updateListener : updateListeners) {
            updateListener.updatedStatistics(fieldStatistics);
        }
    }

    private void setStatisticsList(List<FieldStatistics> fieldStatisticsList) {
        checkSwingThread();
        this.fieldStatisticsList = fieldStatisticsList;
        if (fieldStatisticsList != null) {
            analysisTree = AnalysisTree.create(fieldStatisticsList);
        }
        else {
            analysisTree = AnalysisTree.create("Analysis not yet performed");
        }
        analysisTreeModel.setRoot(analysisTree.getRoot());
        if (getRecordRoot() != null) {
            AnalysisTree.setRecordRoot(analysisTreeModel, getRecordRoot());
        }
        if (getUniqueElement() != null) {
            AnalysisTree.setUniqueElement(analysisTreeModel, getUniqueElement());
        }
        setStatistics(null);
    }

    private class RecordScanner implements Runnable {
        private ScanPredicate scanPredicate;
        private ProgressListener progressListener;

        private RecordScanner(ScanPredicate scanPredicate, ProgressListener progressListener) {
            this.scanPredicate = scanPredicate;
            this.progressListener = progressListener;
        }

        @Override
        public void run() {
            Path recordRoot = getRecordRoot();
            if (recordRoot == null) {
                return;
            }
            try {
                if (metadataParser == null) {
                    metadataParser = new MetadataParser(dataSetStore.createXmlInputStream(), recordRoot, getRecordCount());
                }
                metadataParser.setProgressListener(progressListener);
                while ((metadataRecord = metadataParser.nextRecord()) != null) {
                    if (scanPredicate == null || scanPredicate.accept(metadataRecord)) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                for (ParseListener parseListener : parseListeners) {
                                    parseListener.updatedRecord(metadataRecord);
                                }
                                if (progressListener != null) {
                                    progressListener.finished(true);
                                }
                            }
                        });
                        break;
                    }
                }
            }
            catch (Exception e) {
                userNotifier.tellUser("Unable to fetch the next record", e);
                metadataParser = null;
            }
        }
    }

    private class MappingSaveTimer implements MappingModel.Listener, ActionListener, Runnable {
        private Timer timer = new Timer(200, this);

        private MappingSaveTimer() {
            timer.setRepeats(false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            executor.execute(this);
        }

        @Override
        public void run() {
            try {
                RecordMapping recordMapping = mappingModel.getRecordMapping();
                if (recordMapping != null) {
                    factModel.fillRecordMapping(recordMapping);
                    dataSetStore.setRecordMapping(recordMapping);
                    log.info("Mapping saved!");
                }
                else {
                    log.warn("No mapping to save - why?"); // todo
                }
            }
            catch (FileStoreException e) {
                userNotifier.tellUser("Unable to save mapping", e);
            }
        }

        @Override
        public void mappingChanged(RecordMapping recordMapping) {
            log.info("Mapping changed");
            timer.restart();
        }
    }

    private class AppConfigSetter implements Runnable {
        private AppConfig appConfig;

        private AppConfigSetter(AppConfig appConfig) {
            this.appConfig = appConfig;
        }

        @Override
        public void run() {
            try {
                fileStore.setAppConfig(appConfig);
            }
            catch (FileStoreException e) {
                userNotifier.tellUser("Unable to save application configuration", e);
            }
        }
    }

    private class FactModelAdapter implements FactModel.Listener {
        @Override
        public void updatedFact(FactModel factModel, boolean interactive) {
            if (interactive) {
                RecordMapping recordMapping = getMappingModel().getRecordMapping();
                if (recordMapping != null && factModel.fillRecordMapping(recordMapping)) {
                    mappingSaveTimer.mappingChanged(recordMapping);
                }
                if (factModel.fillFacts(facts)) {
                    executor.execute(new FactsSetter(facts));
                }
            }
        }
    }

    private class FactsSetter implements Runnable {
        private Facts facts;

        private FactsSetter(Facts facts) {
            this.facts = facts;
        }

        @Override
        public void run() {
            try {
                dataSetStore.setFacts(facts);
                for (String prefix : dataSetStore.getMappingPrefixes()) {
                    RecordMapping recordMapping = dataSetStore.getRecordMapping(prefix);
                    if (factModel.fillRecordMapping(recordMapping)) {
                        dataSetStore.setRecordMapping(recordMapping);
                    }
                }
            }
            catch (FileStoreException e) {
                userNotifier.tellUser("Unable to save facts", e);
            }
        }
    }

    private static void checkWorkerThread() {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new RuntimeException("Expected Worker thread");
        }
    }

    private static void checkSwingThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new RuntimeException("Expected Swing thread");
        }
    }
}
