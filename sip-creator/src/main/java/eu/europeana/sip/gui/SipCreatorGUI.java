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

package eu.europeana.sip.gui;

import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.MetadataModelImpl;
import eu.delving.sip.AppConfig;
import eu.delving.sip.DataSetClient;
import eu.delving.sip.DataSetInfo;
import eu.delving.sip.FileStore;
import eu.delving.sip.FileStoreException;
import eu.delving.sip.FileStoreImpl;
import eu.europeana.sip.core.GroovyCodeResource;
import eu.europeana.sip.core.RecordValidationException;
import eu.europeana.sip.model.AppConfigModel;
import eu.europeana.sip.model.SipModel;
import eu.europeana.sip.model.UserNotifier;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The main GUI class for the sip creator
 *
 * @author Gerald de Jong, Beautiful Code BV, <geralddejong@gmail.com>
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */

public class SipCreatorGUI extends JFrame {
    private static final String LOCAL_SETS = "Local Data Sets";
    private static final String LOCAL_AND_REMOTE_SETS = "Local and Remote Data Sets";
    private static final String CONNECT_TO = "Connect to repository at %s";
    private static final Dimension SIZE = new Dimension(1024, 768);
    private static final int MARGIN = 15;
    private Logger log = Logger.getLogger(getClass());
    private SipModel sipModel;
    private JLabel titleLabel = new JLabel(LOCAL_SETS, JLabel.CENTER);
    private JTextField filter = new JTextField(10);
    private Timer filterTimer;
    private DataSetClient dataSetClient;
    private JCheckBox connectedBox;
    private DataSetListModel dataSetListModel;
    private DataSetActions dataSetActions;
    private JList dataSetList;

    public SipCreatorGUI() throws FileStoreException {
        super("Delving SIP Creator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        MetadataModel metadataModel = loadMetadataModel();
        File fileStoreDirectory = getFileStoreDirectory();
        FileStore fileStore = new FileStoreImpl(fileStoreDirectory, metadataModel);
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource();
        this.dataSetListModel  = new DataSetListModel(new DataSetListModel.ConnectedStatus() {
            @Override
            public boolean isConnected() {
                return connectedBox.isSelected();
            }
        });
        this.dataSetList = new JList(dataSetListModel);
        this.sipModel = new SipModel(fileStore, metadataModel, groovyCodeResource, new PopupExceptionHandler());
        this.dataSetClient = new DataSetClient(new DataSetClient.Context() {

            @Override
            public String getServerUrl() {
                return sipModel.getAppConfigModel().getServerUrl();
            }

            @Override
            public String getAccessKey() {
                return sipModel.getAppConfigModel().getAccessKey();
            }

            @Override
            public void setInfo(DataSetInfo dataSetInfo) {
                dataSetListModel.setDataSetInfo(dataSetInfo);
                dataSetActions.setDataSetInfo(dataSetInfo);
            }

            @Override
            public void setList(List<DataSetInfo> list) {
                if (list != null) {
                    Set<String> untouched = dataSetListModel.setDataSetInfoList(list);
                    if (!untouched.isEmpty()) {
                        dataSetActions.setUntouched(untouched);
                    }
                    for (DataSetInfo dataSetInfo : list) {
                        dataSetActions.setDataSetInfo(dataSetInfo);
                    }
                }
                else {
                    log.warn("received empty list from the server");
                }
            }

            @Override
            public void tellUser(String message) {
                sipModel.getUserNotifier().tellUser(message);
            }

            @Override
            public void disconnected() {
                connectedBox.setSelected(false);
                sipModel.getUserNotifier().tellUser(String.format("Disconnected from Repository at %s", sipModel.getAppConfigModel().getServerHostPort()));
            }
        });
        dataSetActions = new DataSetActions(this, sipModel, dataSetClient, new Runnable() {
            @Override
            public void run() {
                dataSetListModel.clear();
                for (FileStore.DataSetStore dataSetStore : sipModel.getFileStore().getDataSetStores().values()) {
                    dataSetListModel.setDataSetStore(dataSetStore);
                }
                dataSetListModel.setPattern(filter.getText());
            }
        });
        setJMenuBar(createMenuBar());
        JPanel main = new JPanel(new BorderLayout(MARGIN, MARGIN));
        main.setBorder(BorderFactory.createEmptyBorder(MARGIN, MARGIN, MARGIN, MARGIN));
        JPanel north = new JPanel(new BorderLayout());
        north.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createEmptyBorder(MARGIN, MARGIN, 0, MARGIN),
                        BorderFactory.createRaisedBevelBorder()
                )
        );
        titleLabel.setOpaque(true);
        titleLabel.setFont(new Font("Sans", Font.BOLD, 24));
        JLabel northRight = new JLabel(new ImageIcon(getClass().getResource("/delving-logo.png")));
        north.add(createFilterPanel(), BorderLayout.WEST);
        north.add(titleLabel, BorderLayout.CENTER);
        north.add(northRight, BorderLayout.EAST);
        getContentPane().add(north, BorderLayout.NORTH);
        getContentPane().add(main, BorderLayout.CENTER);
        JPanel south = new JPanel(new GridLayout(1, 0));
        south.add(createConnectPanel());
        south.add(createFinishedPanel());
        getContentPane().add(south, BorderLayout.SOUTH);
        main.add(createList(), BorderLayout.CENTER);
        main.add(dataSetActions.getPanel(), BorderLayout.EAST);
        setSize(SIZE);
        setLocation((Toolkit.getDefaultToolkit().getScreenSize().width - SIZE.width) / 2, (Toolkit.getDefaultToolkit().getScreenSize().height - SIZE.height) / 2);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                for (FileStore.DataSetStore dataSetStore : sipModel.getFileStore().getDataSetStores().values()) {
                    dataSetListModel.setDataSetStore(dataSetStore);
                }
            }
        });
    }

    private Component createFilterPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Filter"));
        filterTimer = new Timer(300, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                dataSetList.clearSelection();
                dataSetListModel.setPattern(filter.getText());
            }
        });
        filterTimer.setRepeats(false);
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                filterTimer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                filterTimer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
                filterTimer.restart();
            }
        });
        p.add(filter, BorderLayout.CENTER);
        return p;
    }

    private File getFileStoreDirectory() throws FileStoreException {
        File fileStore = new File(System.getProperty("user.home"), "/sip-creator-file-store");
        if (fileStore.isFile()) {
            try {
                List<String> lines = FileUtils.readLines(fileStore);
                String directory;
                if (lines.size() == 1) {
                    directory = lines.get(0);
                }
                else {
                    directory = (String) JOptionPane.showInputDialog(null,
                            "Please choose file store", "Launch SIP-Creator", JOptionPane.PLAIN_MESSAGE, null,
                            lines.toArray(), "");
                }
                if (directory == null) {
                    System.exit(0);
                }
                fileStore = new File(directory);
                if (fileStore.exists() && !fileStore.isDirectory()) {
                    throw new FileStoreException(String.format("%s is not a directory", fileStore.getAbsolutePath()));
                }
            }
            catch (IOException e) {
                throw new FileStoreException("Unable to read the file " + fileStore.getAbsolutePath());
            }
        }
        return fileStore;
    }

    private JPanel createConnectPanel() {
        connectedBox = new JCheckBox(String.format(CONNECT_TO, sipModel.getAppConfigModel().getServerHostPort()));
        sipModel.getAppConfigModel().addListener(new AppConfigModel.Listener() {
            @Override
            public void appConfigUpdated(AppConfig appConfig) {
                connectedBox.setText(String.format(CONNECT_TO, sipModel.getAppConfigModel().getServerHostPort()));
            }
        });
        connectedBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                boolean enabled = itemEvent.getStateChange() == ItemEvent.SELECTED;
                dataSetClient.setListFetchingEnabled(enabled);
                if (!enabled) {
                    dataSetListModel.clear();
                    for (FileStore.DataSetStore dataSetStore : sipModel.getFileStore().getDataSetStores().values()) {
                        dataSetListModel.setDataSetStore(dataSetStore);
                    }
                }
                titleLabel.setText(enabled ? LOCAL_AND_REMOTE_SETS : LOCAL_SETS);
            }
        });
        JPanel p = new JPanel(new FlowLayout());
        p.add(connectedBox);
        return p;
    }

    private JPanel createFinishedPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        Action finishedAction = new FinishedAction(this);
        ((JComponent) getContentPane()).getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ESCAPE"), "finished");
        ((JComponent) getContentPane()).getActionMap().put("finished", finishedAction);
        JButton hide = new JButton(finishedAction);
        panel.add(hide);
        return panel;
    }

    private class FinishedAction extends AbstractAction {

        private JFrame frame;

        private FinishedAction(JFrame frame) {
            this.frame = frame;
            putValue(NAME, "Finished (ESCAPE)");
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke("ESC"));
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            switch (JOptionPane.showConfirmDialog(SipCreatorGUI.this, "Are you sure you want to quit?", "Quit SIP-Creator?", JOptionPane.OK_CANCEL_OPTION)) {
                case JOptionPane.CANCEL_OPTION:
                    break;
                case JOptionPane.OK_OPTION:
                    frame.setVisible(false);
                    System.exit(0);
                    break;
            }
        }
    }

    private JComponent createList() {
        dataSetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dataSetList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent event) {
                if (event.getValueIsAdjusting()) return;
                int selected = dataSetList.getSelectedIndex();
                DataSetListModel.Entry selectedEntry = selected >= 0 ? dataSetListModel.getEntry(selected) : null;
                dataSetActions.setEntry(selectedEntry);
            }
        });
        dataSetList.setCellRenderer(new DataSetListModel.Cell());
        dataSetList.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        dataSetList.setBackground(getBackground());
        JScrollPane scroll = new JScrollPane(dataSetList);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        return scroll;
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.add(new ImportMenu(this, sipModel, new Runnable() {
            @Override
            public void run() {
                for (FileStore.DataSetStore dataSetStore : sipModel.getFileStore().getDataSetStores().values()) {
                    dataSetListModel.setDataSetStore(dataSetStore);
                }
            }
        }));
        bar.add(new RepositoryMenu(this, sipModel));
        bar.add(dataSetActions.createPrefixActivationMenu());
        return bar;
    }

    private MetadataModel loadMetadataModel() {
        try {
            MetadataModelImpl metadataModel = new MetadataModelImpl();
            metadataModel.setRecordDefinitionResources(Arrays.asList(
                    "/ese-record-definition.xml",
                    "/icn-record-definition.xml",
                    "/abm-record-definition.xml"
            ));
            metadataModel.setDefaultPrefix("ese");
            return metadataModel;
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
            return null;
        }
    }

    private class PopupExceptionHandler implements UserNotifier {

        @Override
        public void tellUser(final String message, final Exception exception) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    String html = exception != null ?
                            String.format("<html><h3>%s</h3><p>%s</p></html>", message, exception.getMessage()) :
                            String.format("<html><h3>%s</h3></html>", message);
                    if (exception instanceof RecordValidationException) {
                        RecordValidationException rve = (RecordValidationException) exception;
                        StringBuilder problemHtml = new StringBuilder(String.format("<html><h3>%s</h3><ul>", message));
                        for (String problem : rve.getProblems()) {
                            problemHtml.append(String.format("<li>%s</li>", problem));
                        }
                        problemHtml.append("</ul></html>");
                        html = problemHtml.toString();
                    }
                    JOptionPane.showMessageDialog(null, html);
                }
            });
            if (exception != null) {
                log.warn(message, exception);
            }
            else {
                log.warn(message);
            }
        }

        @Override
        public void tellUser(String message) {
            tellUser(message, null);
        }
    }

    public static void main(final String[] args) throws ClassNotFoundException {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    SipCreatorGUI sipCreatorGUI = new SipCreatorGUI();
                    sipCreatorGUI.setVisible(true);
                }
                catch (FileStoreException e) {
                    JOptionPane.showMessageDialog(null, "Unable to create the file store");
                    e.printStackTrace();
                }
            }
        });
    }
}