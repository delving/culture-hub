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

package eu.europeana.sip.gui;

import eu.delving.metadata.Hasher;
import eu.delving.sip.DataSetClient;
import eu.delving.sip.DataSetCommand;
import eu.delving.sip.DataSetInfo;
import eu.delving.sip.DataSetState;
import eu.delving.sip.FileStore;
import eu.delving.sip.FileStoreException;
import eu.delving.sip.FileType;
import eu.delving.sip.ProgressListener;
import eu.europeana.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.ProgressMonitor;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * All the actions that can be launched when a data set is selected
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class DataSetActions {
    private static final Dimension SIZE = new Dimension(1024, 768);
    private JFrame frame;
    private RecordStatisticsDialog recordStatisticsDialog;
    private AnalysisFactsDialog analysisFactsDialog;
    private MappingDialog mappingDialog;
    private SipModel sipModel;
    private DataSetClient dataSetClient;
    private Runnable refreshList;
    private DataSetListModel.Entry entry;
    private List<DataSetAction> localActions = new ArrayList<DataSetAction>();
    private List<DataSetAction> remoteActions = new ArrayList<DataSetAction>();
    private List<DataSetAction> actions = new ArrayList<DataSetAction>();
    private JPanel panel;

    public DataSetActions(JFrame frame, SipModel sipModel, DataSetClient dataSetClient, Runnable refreshList) {
        this.frame = frame;
        this.sipModel = sipModel;
        this.dataSetClient = dataSetClient;
        this.refreshList = refreshList;
        this.recordStatisticsDialog = new RecordStatisticsDialog(sipModel);
        this.analysisFactsDialog = new AnalysisFactsDialog(sipModel);
        this.mappingDialog = new MappingDialog(sipModel);
    }

    public JPanel getPanel() {
        if (panel == null) {
            panel = new JPanel();
            buildPanel();
        }
        return panel;
    }

    public JMenu createPrefixActivationMenu() {
        JMenu menu = new JMenu("Mappings");
        List<String> activePrefixes = sipModel.getAppConfigModel().getActiveMetadataPrefixes();
        for (final String prefix : sipModel.getMetadataModel().getPrefixes()) {
            final JCheckBoxMenuItem box = new JCheckBoxMenuItem(String.format("Mapping '%s'", prefix));
            if (activePrefixes.contains(prefix)) {
                box.setState(true);
            }
            box.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    sipModel.getAppConfigModel().setMetadataPrefixActive(prefix, box.getState());
                    panel.removeAll();
                    buildPanel();
                    panel.invalidate();
                    frame.getContentPane().validate();
                    setEntry(null);
                }
            });
            menu.add(box);
        }
        return menu;
    }

    public void setEntry(DataSetListModel.Entry entry) {
        this.entry = entry;
        for (DataSetAction dataSetAction : actions) {
            dataSetAction.setEntry(entry);
        }
    }

    public void setDataSetInfo(DataSetInfo dataSetInfo) {
        if (entry != null) {
            if (dataSetInfo == null) {
                setEntry(null);
            }
            else if (entry.getDataSetInfo() != null && entry.getDataSetInfo().spec.equals(dataSetInfo.spec)) {
                setEntry(entry);
            }
        }
    }

    public void setUntouched(Set<String> untouched) {
        if (entry != null && untouched.contains(entry.getSpec())) {
            entry.setDataSetInfo(null);
            setEntry(entry);
        }
    }

    private void buildPanel() {
        createLocalActions(sipModel);
        createRemoteActions();
        actions.clear();
        actions.addAll(localActions);
        actions.addAll(remoteActions);
        JPanel local = createLocalPanel();
        JPanel remote = createRemotePanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
        panel.add(local);
        panel.add(Box.createVerticalGlue());
        panel.add(remote);
        panel.add(Box.createVerticalGlue());
        setEntry(entry);
    }

    private JPanel createRemotePanel() {
        JPanel remote = new JPanel(new GridLayout(0, 1, 5, 5));
        remote.setBorder(BorderFactory.createTitledBorder("Remote Actions"));
        for (Action action : remoteActions) {
            remote.add(new JButton(action));
        }
        return remote;
    }

    private JPanel createLocalPanel() {
        JPanel local = new JPanel(new GridLayout(0, 1, 5, 5));
        local.setBorder(BorderFactory.createTitledBorder("Local Actions"));
        for (Action action : localActions) {
            local.add(new JButton(action));
        }
        return local;
    }

    private void createLocalActions(SipModel sipModel) {
        localActions.clear();
        localActions.add(createDownloadDataSetAction());
        localActions.add(createAnalyzeFactsAction());
        for (String metadataPrefix : sipModel.getAppConfigModel().getActiveMetadataPrefixes()) {
            localActions.add(createEditMappingAction(metadataPrefix));
        }
        localActions.add(createRecordStatisticsAction());
        localActions.add(createDeleteLocalAction());
    }

    private void createRemoteActions() {
        remoteActions.clear();
        remoteActions.add(createUploadFactsAction());
        remoteActions.add(createUploadSourceAction());
        for (String prefix : sipModel.getAppConfigModel().getActiveMetadataPrefixes()) {
            remoteActions.add(createUploadMappingAction(prefix));
        }
        for (DataSetCommand command : DataSetCommand.values()) {
            remoteActions.add(createCommandAction(command, command == DataSetCommand.DELETE));
        }
    }

    abstract class DataSetAction extends AbstractAction {

        protected DataSetAction(String s) {
            super(s);
        }

        void setEntry(DataSetListModel.Entry entry) {
            if (entry == null) {
                setEnabled(false);
            }
            else {
                setEnabled(isEnabled(entry));
            }
        }

        abstract boolean isEnabled(DataSetListModel.Entry entry);
    }

    private DataSetAction createRecordStatisticsAction() {
        return new DataSetAction("Gather Record Statistics") {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                sipModel.setDataSetStore(entry.getDataSetStore());
                recordStatisticsDialog.reveal();
            }

            @Override
            boolean isEnabled(DataSetListModel.Entry entry) {
                return entry.getDataSetStore() != null &&
                        entry.getDataSetStore().getFacts().isValid();
            }
        };
    }

    private DataSetAction createDownloadDataSetAction() {
        return new DataSetAction("Download Data Set") {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                DataSetInfo info = entry.getDataSetInfo();
                ProgressMonitor progressMonitor = new ProgressMonitor(frame, "Downloading", String.format("Downloading source for %s", info.spec), 0, 100);
                final ProgressListener progressListener = new ProgressListener.Adapter(progressMonitor) {
                    @Override
                    public void swingFinished(boolean success) {
                        setEnabled(!success);
                        setEntry(entry);
                    }
                };
                try {
                    FileStore.DataSetStore store = sipModel.getFileStore().createDataSetStore(info.spec);
                    dataSetClient.downloadDataSet(store, progressListener);
                    entry.setDataSetStore(store);
                }
                catch (FileStoreException e) {
                    sipModel.getUserNotifier().tellUser(String.format("Unable to create data set %s", info.spec));
                }
            }

            @Override
            boolean isEnabled(DataSetListModel.Entry entry) {
                return entry.getDataSetInfo() != null && entry.getDataSetStore() == null;
            }
        };
    }

    private DataSetAction createAnalyzeFactsAction() {
        return new DataSetAction("Analyze Fields & Edit Facts") {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                sipModel.setDataSetStore(entry.getDataSetStore());
                analysisFactsDialog.reveal();
            }

            @Override
            boolean isEnabled(DataSetListModel.Entry entry) {
                return entry.getDataSetStore() != null;
            }
        };
    }

    private DataSetAction createEditMappingAction(final String metadataPrefix) {
        return new DataSetAction(String.format("Edit '%s' Mapping", metadataPrefix)) {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                sipModel.setDataSetStore(entry.getDataSetStore());
                sipModel.setMetadataPrefix(metadataPrefix);
                mappingDialog.reveal(metadataPrefix);
            }

            @Override
            boolean isEnabled(DataSetListModel.Entry entry) {
                return entry.getDataSetStore() != null && entry.getDataSetStore().hasSource() && entry.getDataSetStore().getStatistics() != null;
            }
        };
    }

    private DataSetAction createDeleteLocalAction() {
        return new DataSetAction("Delete Local Data Set") {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                int doImport = JOptionPane.showConfirmDialog(
                        frame,
                        String.format(
                                "<html>Are you sure you wish to delete the local storage for the <strong>%s</strong> data set?",
                                entry.getDataSetStore().getSpec()
                        ),
                        "Verify your choice",
                        JOptionPane.YES_NO_OPTION
                );
                if (doImport == JOptionPane.YES_OPTION) {
                    try {
                        entry.getDataSetStore().delete();
                        if (entry.getDataSetInfo() == null) {
                            refreshList.run();
                        }
                        else {
                            entry.setDataSetStore(null);
                        }
                    }
                    catch (FileStoreException e) {
                        sipModel.getUserNotifier().tellUser("Unable to delete data set", e);
                    }
                }
            }

            @Override
            boolean isEnabled(DataSetListModel.Entry entry) {
                return entry.getDataSetStore() != null;
            }
        };
    }

    private boolean canUpload(FileType fileType, File file, DataSetListModel.Entry entry) {
        if (!dataSetClient.isConnected() || file == null || !file.exists()) {
            return false;
        }
        if (entry.getDataSetInfo() == null) {
            return fileType == FileType.FACTS;
        }
        String hash = Hasher.extractHashFromFileName(file.getName());
        return !entry.getDataSetInfo().hasHash(hash);
    }

    private DataSetAction createUploadFactsAction() {
        return new DataSetAction("Upload Facts") {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                FileStore.DataSetStore store = entry.getDataSetStore();
                if (!store.getFacts().isValid()) {
                    sipModel.getUserNotifier().tellUser("Sorry, but there are still facts that must be filled in.");
                    return;
                }
                ProgressMonitor progressMonitor = new ProgressMonitor(frame, "Uploading", String.format("Uploading facts for %s", store.getSpec()), 0, 100);
                final ProgressListener progressListener = new ProgressListener.Adapter(progressMonitor) {
                    @Override
                    public void swingFinished(boolean success) {
                        setEnabled(!success);
                    }
                };
                sipModel.setDataSetStore(store);
                dataSetClient.uploadFile(FileType.FACTS, store.getSpec(), store.getFactsFile(), progressListener);
            }

            @Override
            boolean isEnabled(DataSetListModel.Entry entry) {
                FileStore.DataSetStore store = entry.getDataSetStore();
                return store != null && canUpload(FileType.FACTS, entry.getDataSetStore().getFactsFile(), entry);
            }
        };
    }

    private DataSetAction createUploadSourceAction() {
        return new DataSetAction("Upload Source") {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                final FileStore.DataSetStore store = entry.getDataSetStore();
                ProgressMonitor progressMonitor = new ProgressMonitor(frame, "Uploading", String.format("Uploading source for %s", store.getSpec()), 0, 100);
                final ProgressListener progressListener = new ProgressListener.Adapter(progressMonitor) {
                    @Override
                    public void swingFinished(boolean success) {
                        setEnabled(!success);
                    }
                };
                sipModel.setDataSetStore(store);
                dataSetClient.uploadFile(FileType.SOURCE, store.getSpec(), store.getSourceFile(), progressListener);
            }

            @Override
            boolean isEnabled(DataSetListModel.Entry entry) {
                FileStore.DataSetStore store = entry.getDataSetStore();
                return store != null &&
                        canUpload(FileType.SOURCE, entry.getDataSetStore().getSourceFile(), entry) &&
                        !canUpload(FileType.FACTS, entry.getDataSetStore().getFactsFile(), entry);
            }
        };
    }

    private DataSetAction createUploadMappingAction(final String prefix) {
        return new DataSetAction(String.format("Upload %s Mapping", prefix)) {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                final FileStore.DataSetStore store = entry.getDataSetStore();
                ProgressMonitor progressMonitor = new ProgressMonitor(frame, "Uploading", String.format("Uploading %s mapping for %s ", prefix, store.getSpec()), 0, 100);
                final ProgressListener progressListener = new ProgressListener.Adapter(progressMonitor) {
                    @Override
                    public void swingFinished(boolean success) {
                        setEnabled(!success);
                    }
                };
                sipModel.setDataSetStore(store);
                dataSetClient.uploadFile(FileType.MAPPING, store.getSpec(), store.getMappingFile(prefix), progressListener);
            }

            @Override
            boolean isEnabled(DataSetListModel.Entry entry) {
                FileStore.DataSetStore store = entry.getDataSetStore();
                return store != null && canUpload(FileType.MAPPING, store.getMappingFile(prefix), entry);
            }

        };
    }

    private DataSetAction createCommandAction(final DataSetCommand command, final boolean verify) {
        return new DataSetAction(getCommandName(command)) {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                boolean justDoIt = true;
                if (verify) {
                    int doImport = JOptionPane.showConfirmDialog(
                            frame,
                            String.format(
                                    "<html>Are you sure you wish to %s data set %s in the repository?",
                                    getCommandName(command),
                                    entry.getDataSetStore().getSpec()
                            ),
                            "Verify your choice",
                            JOptionPane.YES_NO_OPTION
                    );
                    if (doImport != JOptionPane.YES_OPTION) {
                        justDoIt = false;
                    }
                }
                if (justDoIt) {
                    dataSetClient.sendCommand(entry.getDataSetInfo().spec, command);
                }
            }

            @Override
            boolean isEnabled(DataSetListModel.Entry entry) {
                DataSetInfo info = entry.getDataSetInfo();
                if (info == null) {
                    return false;
                }
                else switch (DataSetState.valueOf(info.state)) {
                    case INCOMPLETE:
                        switch (command) {
                            case DELETE:
                                return true;
                            default:
                                return false;
                        }
                    case UPLOADED:
                        switch (command) {
                            case INDEX:
                            case DELETE:
                                return true;
                            default:
                                return false;
                        }
                    case QUEUED:
                    case INDEXING:
                        switch (command) {
                            case DISABLE:
                                return true;
                            default:
                                return false;
                        }
                    case ENABLED:
                        switch (command) {
                            case DISABLE:
                            case REINDEX:
                                return true;
                            default:
                                return false;
                        }
                    case DISABLED:
                        switch (command) {
                            case INDEX:
                            case DELETE:
                                return true;
                            default:
                                return false;
                        }
                    case ERROR:
                        switch (command) {
                            case DISABLE:
                            case DELETE:
                                return true;
                            default:
                                return false;
                        }
                    default:
                        throw new RuntimeException();
                }
            }
        };
    }

    private String getCommandName(DataSetCommand command) {
        String name;
        switch (command) {
            case INDEX:
                name = "Index";
                break;
            case DISABLE:
                name = "Disable";
                break;
            case REINDEX:
                name = "Re-index";
                break;
            case DELETE:
                name = "Delete";
                break;
            default:
                throw new RuntimeException();
        }
        return name;
    }

    private class RecordStatisticsDialog extends JDialog {
        private SipModel sipModel;

        private RecordStatisticsDialog(SipModel sipModel) throws HeadlessException {
            super(frame, "Analysis & Facts", true);
            this.sipModel = sipModel;
            getContentPane().add(new RecordStatisticsPanel(sipModel));
            getContentPane().add(createFinishedPanel(this), BorderLayout.SOUTH);
            setSize(SIZE);
            setLocation((Toolkit.getDefaultToolkit().getScreenSize().width - SIZE.width) / 2, (Toolkit.getDefaultToolkit().getScreenSize().height - SIZE.height) / 2);
        }

        public void reveal() {
            setTitle(String.format("Record Statistics for '%s'", sipModel.getDataSetStore().getSpec()));
            setVisible(true);
        }
    }

    private class AnalysisFactsDialog extends JDialog {
        private SipModel sipModel;

        private AnalysisFactsDialog(SipModel sipModel) throws HeadlessException {
            super(frame, "Analysis & Facts", true);
            this.sipModel = sipModel;
            getContentPane().add(new AnalysisFactsPanel(sipModel));
            getContentPane().add(createFinishedPanel(this), BorderLayout.SOUTH);
            setSize(SIZE);
            setLocation((Toolkit.getDefaultToolkit().getScreenSize().width - SIZE.width) / 2, (Toolkit.getDefaultToolkit().getScreenSize().height - SIZE.height) / 2);
        }

        public void reveal() {
            setTitle(String.format("Analysis & Facts for '%s'", sipModel.getDataSetStore().getSpec()));
            setVisible(true);
        }
    }

    private class MappingDialog extends JDialog {
        private SipModel sipModel;

        private MappingDialog(SipModel sipModel) throws HeadlessException {
            super(frame, "Mapping", true);
            this.sipModel = sipModel;
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Mapping", new MappingPanel(sipModel));
            tabs.addTab("Refinement", new RefinementPanel(this, sipModel));
            tabs.addTab("Normalization", new NormalizationPanel(sipModel));
            getContentPane().add(tabs, BorderLayout.CENTER);
            getContentPane().add(createFinishedPanel(this), BorderLayout.SOUTH);
            setSize(SIZE);
            setLocation((Toolkit.getDefaultToolkit().getScreenSize().width - SIZE.width) / 2, (Toolkit.getDefaultToolkit().getScreenSize().height - SIZE.height) / 2);
            setJMenuBar(createMappingMenuBar());
        }

        private JMenuBar createMappingMenuBar() {
            JMenuBar bar = new JMenuBar();
            MappingTemplateMenu mappingTemplateMenu = new MappingTemplateMenu(this, sipModel);
            bar.add(mappingTemplateMenu);
            return bar;
        }

        public void reveal(String prefix) {
            setTitle(String.format("Mapping '%s' of '%s'", prefix, sipModel.getDataSetStore().getSpec()));
            setVisible(true);
        }
    }

    private JPanel createFinishedPanel(JDialog dialog) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        Action finishedAction = new FinishedAction(dialog);
        ((JComponent) dialog.getContentPane()).getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ESCAPE"), "finished");
        ((JComponent) dialog.getContentPane()).getActionMap().put("finished", finishedAction);
        JButton hide = new JButton(finishedAction);
        panel.add(hide);
        return panel;
    }

    private class FinishedAction extends AbstractAction {

        private JDialog dialog;

        private FinishedAction(JDialog dialog) {
            this.dialog = dialog;
            putValue(NAME, "Finished (ESCAPE)");
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke("ESC"));
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            setEntry(entry);
            dialog.setVisible(false);
        }
    }
}
