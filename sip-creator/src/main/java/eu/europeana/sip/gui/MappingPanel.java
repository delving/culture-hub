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

import eu.delving.metadata.CodeGenerator;
import eu.delving.metadata.FieldDefinition;
import eu.delving.metadata.FieldMapping;
import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.Path;
import eu.delving.metadata.SourceVariable;
import eu.delving.sip.FileStore;
import eu.europeana.sip.model.FieldListModel;
import eu.europeana.sip.model.FieldMappingListModel;
import eu.europeana.sip.model.SipModel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.List;

/**
 * A Graphical interface for analysis
 *
 * @author Gerald de Jong, Beautiful Code BV, <geralddejong@gmail.com>
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */

public class MappingPanel extends JPanel {
    private static final String CREATE = "Create mapping";
    private static final String CREATE_FOR = "Create mapping for '%s'";
    private static final Dimension PREFERRED_SIZE = new Dimension(300, 700);
    private SipModel sipModel;
    private JTextField constantField = new JTextField("?");
    private JButton createMappingButton = new JButton(String.format(CREATE_FOR, "?"));
    private ObviousMappingDialog obviousMappingDialog;
    private JButton createObviousMappingButton = new JButton("Create obvious mappings");
    private JButton removeMappingsButton = new JButton("Remove selected mappings");
    private JList variablesList, mappingList, fieldList;
    private FieldStatisticsPanel fieldStatisticsPanel = new FieldStatisticsPanel();

    public MappingPanel(SipModel sipModel) {
        super(new GridLayout(2, 2, 5, 5));
        this.sipModel = sipModel;
        add(createInputPanel());
        add(createFieldsPanel());
        fieldStatisticsPanel.setPreferredSize(PREFERRED_SIZE);
        add(fieldStatisticsPanel);
        add(createFieldMappingListPanel());
        wireUp();
    }

    private JPanel createInputPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.add(createVariablesPanel(), BorderLayout.CENTER);
        p.add(createConstantFieldPanel(), BorderLayout.SOUTH);
        return p;
    }

    private JPanel createVariablesPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Source Fields"));
        variablesList = new JList(sipModel.getVariablesListWithCountsModel());
        p.add(scroll(variablesList), BorderLayout.CENTER);
        return p;
    }

    private JPanel createConstantFieldPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Constant Value Source"));
        p.add(constantField);
        return p;
    }

    private JPanel createFieldsPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Unmapped Target Fields"));
        fieldList = new JList(sipModel.getUnmappedFieldListModel());
        fieldList.setCellRenderer(new FieldListModel.CellRenderer());
        fieldList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        p.add(scroll(fieldList));
        return p;
    }

    private JPanel createFieldMappingListPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Field Mappings"));
        mappingList = new JList(sipModel.getFieldMappingListModel());
        mappingList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        mappingList.setCellRenderer(new FieldMappingListModel.CellRenderer());
        p.add(scroll(mappingList), BorderLayout.CENTER);
        p.add(createButtonPanel(), BorderLayout.SOUTH);
        return p;
    }

    private JPanel createButtonPanel() {
        JPanel p = new JPanel(new GridLayout(0, 1, 2, 2));
        createMappingButton.setEnabled(false);
        p.add(createMappingButton);
        createObviousMappingButton.setEnabled(false);
        p.add(createObviousMappingButton);
        removeMappingsButton.setEnabled(false);
        p.add(removeMappingsButton);
        return p;
    }

    private JScrollPane scroll(JComponent content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scroll.setPreferredSize(new Dimension(400, 400));
        return scroll;
    }

    private void wireUp() {
        sipModel.addUpdateListener(new SipModel.UpdateListener() {
            @Override
            public void updatedDataSetStore(FileStore.DataSetStore dataSetStore) {
                variablesList.clearSelection();
                fieldList.clearSelection();
                mappingList.clearSelection();
                prepareCreateMappingButtons();
            }

            @Override
            public void updatedStatistics(final FieldStatistics fieldStatistics) {
                fieldStatisticsPanel.setStatistics(fieldStatistics);
            }

            @Override
            public void updatedRecordRoot(Path recordRoot, int recordCount) {
            }

            @Override
            public void normalizationMessage(boolean complete, String message) {
            }
        });
        createMappingButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FieldDefinition fieldDefinition = (FieldDefinition) fieldList.getSelectedValue();
                if (fieldDefinition != null) {
                    CodeGenerator generator = new CodeGenerator();
                    FieldMapping fieldMapping = new FieldMapping(fieldDefinition);
                    generator.generateCodeFor(fieldMapping, createSelectedVariableList(), constantField.getText());
                    sipModel.addFieldMapping(fieldMapping);
                }
                variablesList.clearSelection();
                fieldList.clearSelection();
                int index = mappingList.getModel().getSize() - 1;
                mappingList.clearSelection();
//                prepareCreateMappingButtons();
            }
        });
        createObviousMappingButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                obviousMappingDialog.setVisible(true);
            }
        });
        removeMappingsButton.setEnabled(false);
        removeMappingsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                for (Object value : mappingList.getSelectedValues()) {
                    FieldMapping fieldMapping = (FieldMapping) value;
                    if (fieldMapping != null) {
                        sipModel.removeFieldMapping(fieldMapping);
                    }
                }
            }
        });
        mappingList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                FieldMapping fieldMapping = (FieldMapping) mappingList.getSelectedValue();
                if (fieldMapping != null) {
                    removeMappingsButton.setEnabled(true);
                }
                else {
                    removeMappingsButton.setEnabled(false);
                }
            }
        });
        constantField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                variablesList.clearSelection();
            }

            @Override
            public void focusLost(FocusEvent e) {
            }
        });
        variablesList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) return;
                final SourceVariable sourceVariable = (SourceVariable) variablesList.getSelectedValue();
                if (sourceVariable != null && sourceVariable.hasStatistics()) {
                    sipModel.setStatistics(sourceVariable.getStatistics());
                    constantField.setText("?");
                }
            }
        });
        sipModel.getFieldMappingListModel().addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {
                prepareCreateMappingButtons();
            }

            @Override
            public void intervalRemoved(ListDataEvent e) {
                prepareCreateMappingButtons();
            }

            @Override
            public void contentsChanged(ListDataEvent e) {
                prepareCreateMappingButtons();
            }
        });
        fieldList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                prepareCreateMappingButtons();
            }
        });
    }

    private List<SourceVariable> createSelectedVariableList() {
        List<SourceVariable> list = new ArrayList<SourceVariable>();
        for (Object variableHolderObject : variablesList.getSelectedValues()) {
            list.add((SourceVariable) variableHolderObject);
        }
        return list;
    }

    private void prepareCreateMappingButtons() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                FieldDefinition fieldDefinition = (FieldDefinition) fieldList.getSelectedValue();
                if (fieldDefinition != null) {
                    createMappingButton.setText(String.format(CREATE_FOR, fieldDefinition.getFieldNameString()));
                    createMappingButton.setEnabled(true);
                }
                else {
                    createMappingButton.setText(CREATE);
                    createMappingButton.setEnabled(false);
                }
                CodeGenerator codeGenerator = new CodeGenerator();
                List<FieldMapping> obvious = codeGenerator.createObviousMappings(sipModel.getUnmappedFields(), sipModel.getVariables());
                if (obvious.isEmpty()) {
                    obviousMappingDialog = null;
                    createObviousMappingButton.setEnabled(false);
                }
                else {
                    createObviousMappingButton.setEnabled(true);
                    obviousMappingDialog = new ObviousMappingDialog(getOwningFrame(MappingPanel.this), obvious, new ObviousMappingDialog.Creator() {
                        @Override
                        public void createMapping(FieldMapping mapping) {
                            sipModel.addFieldMapping(mapping);
                        }
                    });
                }
            }
        });
    }

    public static Frame getOwningFrame(Component comp) {
        if (comp == null) {
            throw new IllegalArgumentException("null Component passed");
        }

        if (comp instanceof Frame) {
            return (Frame) comp;
        }
        return getOwningFrame(SwingUtilities.windowForComponent(comp));
    }
}