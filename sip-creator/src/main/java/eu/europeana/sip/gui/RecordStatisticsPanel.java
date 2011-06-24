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

import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.Path;
import eu.delving.sip.FileStore;
import eu.delving.sip.FileStoreException;
import eu.delving.sip.ProgressListener;
import eu.europeana.sip.model.SipModel;
import eu.europeana.sip.xml.RecordAnalyzer;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;

/**
 * A Panel for showing the results of record analysis
 *
 * @author Gerald de Jong, Delving BV, <geralddejong@gmail.com>
 */

public class RecordStatisticsPanel extends JPanel {
    private JEditorPane statisticsView = new JEditorPane();
    private JTextArea codeArea = new JTextArea();
    private SipModel sipModel;
    private int recordCount;

    public RecordStatisticsPanel(SipModel sipModel) {
        super(new BorderLayout(5, 5));
        this.sipModel = sipModel;
        this.sipModel.addUpdateListener(new SipModel.UpdateListener() {

             @Override
             public void updatedDataSetStore(FileStore.DataSetStore dataSetStore) {
             }

             @Override
             public void updatedStatistics(FieldStatistics fieldStatistics) {
             }

             @Override
             public void updatedRecordRoot(Path recordRoot, int recordCount) {
                 if (recordRoot != null) {
                     RecordStatisticsPanel.this.recordCount = recordCount;
                 }
             }

             @Override
             public void normalizationMessage(boolean complete, String message) {
             }
         });
        this.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(createMainPanel(), BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);
    }

    private JPanel createMainPanel() {
        JPanel p = new JPanel(new GridLayout(1, 0, 5, 5));
        p.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        p.add(createCodePanel());
        p.add(createStatisticsPanel());
        return p;
    }

    private JPanel createCodePanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Analysis Code"));
        codeArea.setText(getCode());
        codeArea.setToolTipText(Utility.GROOVY_TOOL_TIP);
        p.add(scroll(codeArea), BorderLayout.CENTER);
        return p;
    }

    private JPanel createStatisticsPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Record Statistics"));
        statisticsView.setContentType("text/html");
        p.add(scroll(statisticsView), BorderLayout.CENTER);
        return p;
    }

    private JScrollPane scroll(JComponent content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scroll.setPreferredSize(new Dimension(300, 500));
        return scroll;
    }

    private JPanel createButtonPanel() {
        JPanel p = new JPanel();
        p.add(new JButton(createPerformAnalysisAction(100, "First 100 Records")));
        p.add(new JButton(createPerformAnalysisAction(1000, "First 1,000 Records")));
        p.add(new JButton(createPerformAnalysisAction(10000, "First 10,000 Records")));
        p.add(new JButton(createPerformAnalysisAction(-1, "All Records")));
        return p;
    }

    private Action createPerformAnalysisAction(final int recordCountLimit, String label) {
        return new AbstractAction(label) {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    sipModel.getFileStore().setCode(FileStore.RECORD_ANALYSIS_FILE_NAME, codeArea.getText());
                    ProgressMonitor progressMonitor = new ProgressMonitor(
                            SwingUtilities.getRoot(RecordStatisticsPanel.this),
                            "<html><h2>Analyzing Records</h2>",
                            "Parsing and analyzing",
                            0,
                            recordCountLimit > 0 ? recordCountLimit : recordCount
                    );
                    sipModel.analyzeRecords(
                            recordCountLimit,
                            new ProgressListener.Adapter(progressMonitor) {
                                @Override
                                public void swingFinished(boolean success) {
                                    setEnabled(true);
                                }
                            },
                            new RecordAnalyzer.Listener() {
                                @Override
                                public void finished(String html) {
                                    statisticsView.setText(html);
                                }
                            }
                    );
                }
                catch (FileStoreException e) {
                    sipModel.getUserNotifier().tellUser("Unable to analyze records", e);
                }
            }
        };
    }

    private String getCode() {
        try {
            return sipModel.getFileStore().getCode(FileStore.RECORD_ANALYSIS_FILE_NAME);
        }
        catch (FileStoreException e) {
            return e.toString();
        }
    }

}
