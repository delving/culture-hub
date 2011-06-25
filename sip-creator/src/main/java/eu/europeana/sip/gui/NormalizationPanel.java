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

import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.Path;
import eu.delving.sip.FileStore;
import eu.delving.sip.ProgressListener;
import eu.europeana.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * Turn diverse source xml data into standardized output for import into the europeana portal database and search
 * engine.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class NormalizationPanel extends JPanel {
    private SipModel sipModel;
    private JCheckBox discardInvalidBox = new JCheckBox("Discard Invalid Records");
    private JCheckBox storeNormalizedBox = new JCheckBox("Store Normalized XML");
    private JLabel normalizeMessageLabel = new JLabel("?", JLabel.CENTER);
    private JFileChooser chooser = new JFileChooser("Normalized Data Output Directory");

    public NormalizationPanel(SipModel sipModel) {
        super(new BorderLayout(5, 5));
        this.sipModel = sipModel;
        JPanel center = new JPanel(new GridLayout(1, 0, 5, 5));
        center.add(new RecordPanel(sipModel, sipModel.getRecordCompileModel()));
        center.add(createOutputPanel());
        add(center, BorderLayout.CENTER);
        add(createNormalizePanel(), BorderLayout.SOUTH);
        wireUp();
    }

    private JPanel createOutputPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Output Record"));
        JTextArea area = new JTextArea(sipModel.getRecordCompileModel().getOutputDocument());
        area.setEditable(false);
        p.add(scroll(area));
        return p;
    }

    private JScrollPane scroll(JComponent content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scroll.setPreferredSize(new Dimension(300, 800));
        return scroll;
    }

    private JPanel createNormalizePanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.add(normalizeMessageLabel, BorderLayout.CENTER);
        p.add(createNormalizeEast(), BorderLayout.EAST);
        return p;
    }

    private JPanel createNormalizeEast() {
        JPanel p = new JPanel(new GridLayout(1, 0, 5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Control"));
        p.add(discardInvalidBox);
        p.add(storeNormalizedBox);
        p.add(new JButton(normalizeAction));
        return p;
    }

    private void wireUp() {
        sipModel.addUpdateListener(new SipModel.UpdateListener() {
            @Override
            public void updatedDataSetStore(FileStore.DataSetStore store) {
            }

            @Override
            public void updatedStatistics(FieldStatistics fieldStatistics) {
            }

            @Override
            public void updatedRecordRoot(Path recordRoot, int recordCount) {
            }

            @Override
            public void normalizationMessage(boolean complete, String message) {
                normalizeMessageLabel.setText(message);
            }
        });
    }

    private Action normalizeAction = new AbstractAction("Normalize") {

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (storeNormalizedBox.isSelected()) {
                File normalizeDirectory = new File(sipModel.getAppConfigModel().getNormalizeDirectory());
                chooser.setSelectedFile(normalizeDirectory); // todo: this doesn't work for some reason
                chooser.setCurrentDirectory(normalizeDirectory.getParentFile());
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setFileFilter(new FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        return file.isDirectory();
                    }

                    @Override
                    public String getDescription() {
                        return "Directories";
                    }
                });
                chooser.setMultiSelectionEnabled(false);
                int choiceMade = chooser.showOpenDialog(NormalizationPanel.this);
                if (choiceMade == JFileChooser.APPROVE_OPTION) {
                    normalizeDirectory = chooser.getSelectedFile();
                    sipModel.getAppConfigModel().setNormalizeDirectory(normalizeDirectory);
                    normalizeTo(normalizeDirectory);
                }
            }
            else {
                normalizeTo(null);
            }
        }

        private void normalizeTo(File normalizeDirectory) {
            String message;
            if (normalizeDirectory != null) {
                message = String.format(
                        "<html><h3>Transforming the raw data of '%s' into '%s' format</h3><br>" +
                                "Writing to %s ",
                        sipModel.getDataSetStore().getSpec(),
                        sipModel.getMappingModel().getRecordMapping().getPrefix(),
                        normalizeDirectory
                );
            }
            else {
                message = String.format(
                        "<html><h3>Transforming the raw data of '%s' into '%s' format</h3>",
                        sipModel.getDataSetStore().getSpec(),
                        sipModel.getMappingModel().getRecordMapping().getPrefix()
                );
            }
            ProgressMonitor progressMonitor = new ProgressMonitor(
                    SwingUtilities.getRoot(NormalizationPanel.this),
                    "<html><h2>Normalizing</h2>",
                    message,
                    0,100
            );
            sipModel.normalize(normalizeDirectory, discardInvalidBox.isSelected(), new ProgressListener.Adapter(progressMonitor){
                @Override
                public void swingFinished(boolean success) {
                    setEnabled(true);
                }
            });
        }
    };
}