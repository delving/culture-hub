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

import eu.delving.metadata.FieldMapping;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Handle checkboxes for a list of field mappings
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class ObviousMappingDialog extends JDialog {

    private List<MappingCheckBox> boxes = new ArrayList<MappingCheckBox>();

    public interface Creator {
        void createMapping(FieldMapping mapping);
    }

    public ObviousMappingDialog(Frame owner, List<FieldMapping> mappings, final Creator creator) {
        super(owner, "Obvious Mappings", true);
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        p.add(createBoxPanel(mappings), BorderLayout.CENTER);
        p.add(createSouthPanel(creator), BorderLayout.SOUTH);
        getContentPane().add(p);
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel createBoxPanel(List<FieldMapping> mappings) {
        JPanel boxPanel = new JPanel(new GridLayout(0, 2, 5, 5));
        boxPanel.setBorder(BorderFactory.createTitledBorder("Fields"));
        for (FieldMapping mapping : mappings) {
            MappingCheckBox box = new MappingCheckBox(mapping);
            boxPanel.add(box);
            boxes.add(box);
        }
        return boxPanel;
    }

    private JPanel createSouthPanel(Creator creator) {
        JPanel p = new JPanel(new GridLayout(0, 1, 5, 5));
        p.add(createSelectPanel());
        p.add(createOkButton(creator));
        return p;
    }

    private JPanel createSelectPanel() {
        JButton all = new JButton("All");
        all.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                for (MappingCheckBox box : boxes) {
                    box.setSelected(true);
                }
            }
        });
        JButton none = new JButton("Clear");
        none.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                for (MappingCheckBox box : boxes) {
                    box.setSelected(false);
                }
            }
        });
        JPanel panel = new JPanel(new GridLayout(1, 0, 5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.add(all);
        panel.add(none);
        return panel;
    }

    private JButton createOkButton(final Creator creator) {
        JButton okButton = new JButton("Generate selected obvious mappings");
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                for (MappingCheckBox box : boxes) {
                    if (box.isSelected()) {
                        creator.createMapping(box.mapping);
                    }
                }
                setVisible(false);
            }
        });
        return okButton;
    }

    private class MappingCheckBox extends JCheckBox {
        private FieldMapping mapping;

        private MappingCheckBox(FieldMapping mapping) {
            super(mapping.getDefinition().getFieldNameString(), true);
            this.mapping = mapping;
        }
    }
}
