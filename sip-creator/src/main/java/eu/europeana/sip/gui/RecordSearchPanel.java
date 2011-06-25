/*
 * Copyright 2011 Delving BV
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

import eu.delving.sip.ProgressListener;
import eu.europeana.sip.core.MetadataRecord;
import eu.europeana.sip.model.SipModel;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ProgressMonitor;
import javax.swing.SpringLayout;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class RecordSearchPanel extends JPanel {
    private SipModel sipModel;
    private List<JTextField> fields = new ArrayList<JTextField>();
    private Listener listener;
    private FieldScanPredicate currentPredicate;

    public interface Listener {
        void searchStarted(String description);

        void searchFinished();
    }

    public RecordSearchPanel(SipModel sipModel, Listener listener) {
        super(new SpringLayout());
        this.sipModel = sipModel;
        this.listener = listener;
        fields.add(createField("Record Number (modulo):", new FieldScanPredicate() {

            private int modulo;

            @Override
            public void setFieldValue(String value) {
                try {
                    modulo = Integer.parseInt(value);
                }
                catch (NumberFormatException e) {
                    modulo = 1;
                }
                if (modulo <= 0) {
                    modulo = 1;
                }
            }

            @Override
            public String render() {
                return String.format("Modulo %d", modulo);
            }

            @Override
            public boolean accept(MetadataRecord record) {
                return modulo == 1 || record.getRecordNumber() % modulo == 0;
            }
        }));
        fields.add(createField("Field Contains (Substring)", new FieldScanPredicate() {
            private String substring;

            @Override
            public void setFieldValue(String value) {
                this.substring = value;
            }

            @Override
            public String render() {
                return String.format("Contains '%s'", substring);
            }

            @Override
            public boolean accept(MetadataRecord record) {
                return record.contains(Pattern.compile(String.format(".*%s.*", substring)));
            }
        }));
        fields.add(createField("Field Equals (RegEx)", new FieldScanPredicate() {
            private String regex;

            @Override
            public void setFieldValue(String value) {
                this.regex = value;
            }

            @Override
            public String render() {
                return String.format("Equals '%s'", regex);
            }

            @Override
            public boolean accept(MetadataRecord record) {
                return record.contains(Pattern.compile(regex));
            }
        }));
        Utility.makeCompactGrid(this, getComponentCount() / 2, 2, 5, 5, 5, 5);
    }

    public void scan(boolean next) {
        if (!next) sipModel.seekFresh();
        ProgressListener progressListener = null;
        if (currentPredicate != null) {
            for (JTextField field : fields) field.setEnabled(false);
            final ProgressMonitor progressMonitor = new ProgressMonitor(
                    SwingUtilities.getRoot(RecordSearchPanel.this),
                    "<html><h2>Scanning</h2>",
                    currentPredicate.render(),
                    0, 100
            );
            progressListener = new ProgressListener.Adapter(progressMonitor) {
                @Override
                public void swingFinished(boolean success) {
                    for (JTextField field : fields) field.setEnabled(true);
                    listener.searchFinished();
                }
            };
        }
        sipModel.seekRecord(currentPredicate, progressListener);
    }

    private JTextField createField(String prompt, final FieldScanPredicate fieldScanPredicate) {
        JLabel label = new JLabel(prompt, JLabel.RIGHT);
        JTextField field = new JTextField();
        label.setLabelFor(field);
        field.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                fieldScanPredicate.setFieldValue(actionEvent.getActionCommand());
                currentPredicate = fieldScanPredicate;
                listener.searchStarted(fieldScanPredicate.render());
                scan(true);
            }
        });
        add(label);
        add(field);
        return field;
    }

    private interface FieldScanPredicate extends SipModel.ScanPredicate {

        void setFieldValue(String value);

        String render();
    }
}
