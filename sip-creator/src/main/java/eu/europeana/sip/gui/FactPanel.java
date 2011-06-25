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

import eu.delving.metadata.FactDefinition;
import eu.delving.metadata.Facts;
import eu.europeana.sip.model.FactModel;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SpringLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * Present a number of fields in a form which can be used as global
 * values during mapping/normalization
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class FactPanel extends JPanel {
    private FactModel factModel;
    private FieldComponent[] fieldComponent;

    public FactPanel(FactModel factModel) {
        super(new SpringLayout());
        setBorder(BorderFactory.createTitledBorder("Facts"));
        this.factModel = factModel;
        factModel.addListener(new ModelAdapter());
        refreshStructure();
    }

    private void refreshStructure() {
        removeAll();
        fieldComponent = new FieldComponent[Facts.definitions().size()];
        int index = 0;
        for (FactDefinition cid : Facts.definitions()) {
            fieldComponent[index++] = new FieldComponent(cid);
        }
        Utility.makeCompactGrid(this, getComponentCount() / 2, 2, 5, 5, 5, 5);
//        setPreferredSize(new Dimension(400, 400));
    }

    public void refreshContent() {
        for (FieldComponent field : fieldComponent) {
            field.getValue();
        }
    }

    private class FieldComponent {
        private FactDefinition factDefinition;
        private JTextField textField;
        private JComboBox comboBox;

        private FieldComponent(FactDefinition factDefinition) {
            this.factDefinition = factDefinition;
            if (factDefinition.options == null) {
                createTextField();
            }
            else {
                createComboBox();
            }
        }

        private void createComboBox() {
            comboBox = new JComboBox(factDefinition.options.toArray());
            comboBox.setSelectedIndex(-1);
            comboBox.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    Object selected = comboBox.getSelectedItem();
                    if (selected != null) {
                        setValue();
                    }
                }
            });
            JLabel label = new JLabel(factDefinition.prompt, JLabel.RIGHT);
            label.setLabelFor(comboBox);
            comboBox.setToolTipText(factDefinition.toolTip);
            label.setToolTipText(factDefinition.toolTip);
            FactPanel.this.add(label);
            FactPanel.this.add(comboBox);
        }

        private void createTextField() {
            textField = new JTextField();
            textField.setText("");
            textField.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent actionEvent) {
                    setValue();
                }
            });
            textField.addFocusListener(new FocusListener() {
                @Override
                public void focusGained(FocusEvent e) {
                }

                @Override
                public void focusLost(FocusEvent e) {
                    setValue();
                }
            });
            if (factDefinition.automatic) {
                textField.setEditable(false);
            }
            JLabel label = new JLabel(factDefinition.prompt, JLabel.RIGHT);
            label.setLabelFor(textField);
            textField.setToolTipText(factDefinition.toolTip);
            label.setToolTipText(factDefinition.toolTip);
            FactPanel.this.add(label);
            FactPanel.this.add(textField);
        }

        private void setValue() {
            if (textField != null) {
                factModel.set(factDefinition, textField.getText());
            }
            else {
                factModel.set(factDefinition, comboBox.getSelectedItem().toString());
            }
        }

        public void getValue() {
            String text = factModel.get(factDefinition);
            if (textField != null) {
                if (text == null) {
                    text = factDefinition.defaultValue;
                }
                textField.setText(text);
            }
            else {
                comboBox.getModel().setSelectedItem(text);
            }
        }
    }

    private class ModelAdapter implements FactModel.Listener {
        @Override
        public void updatedFact(FactModel factModel, boolean interactive) {
            refreshContent();
        }
    }
}
