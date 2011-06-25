package eu.europeana.sip.gui;

import eu.delving.metadata.FieldMapping;

import javax.swing.AbstractAction;
import javax.swing.AbstractListModel;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Allow for editing a value map
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class DictionaryDialog extends JDialog {
    public static final String COPY_VERBATIM = "<<< empty >>>";
    public static final String ASSIGN_SELECTED = "Assign Selected";
    public static final String ASSIGN_ALL = "Assign All";
    private JCheckBox showSet;
    private FieldMapping fieldMapping;
    private MapModel mapModel;
    private JTextField patternField;
    private JButton assign;
    private JComboBox valueBox;
    private JTable table;
    private JLabel statusLabel = new JLabel("", JLabel.CENTER);
    private Runnable finishedRunnable;
    private Timer timer;

    public DictionaryDialog(Dialog owner, FieldMapping fieldMapping, final Runnable finishedRunnable) {
        super(owner, true);
        setTitle(String.format("Dictionary for %s", fieldMapping.getFieldNameString()));
        this.fieldMapping = fieldMapping;
        this.mapModel = new MapModel();
        this.finishedRunnable = finishedRunnable;
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        p.add(createNorth(), BorderLayout.NORTH);
        p.add(new JScrollPane(createTable()), BorderLayout.CENTER);
        p.add(createSouth(), BorderLayout.SOUTH);
        getContentPane().add(p);
        setLocation(owner.getLocation());
        setSize(owner.getSize());
        timer = new Timer(300, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                mapModel.setPattern(patternField.getText(), showSet.isSelected());
            }
        });
        timer.setRepeats(false);
    }

    private JPanel createSouth() {
        JPanel p = new JPanel(new GridLayout(1, 0));
        p.add(statusLabel);
        p.add(createFinishedPanel(this));
        return p;
    }

    private JPanel createNorth() {
        JPanel p = new JPanel(new GridLayout(1, 0));
        p.add(createNorthWest());
        p.add(createNorthEast());
        return p;
    }

    private JPanel createNorthWest() {
        patternField = new JTextField(15);
        patternField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                timer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                timer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
                timer.restart();
            }
        });
        showSet = new JCheckBox("Show Assigned");
        showSet.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                timer.restart();
            }
        });
        JLabel label = new JLabel("Filter:", JLabel.RIGHT);
        label.setLabelFor(patternField);
        JPanel p = new JPanel();
        p.setBorder(BorderFactory.createTitledBorder("Show"));
        p.add(label);
        p.add(patternField);
        p.add(showSet);
        return p;
    }

    private JPanel createNorthEast() {
        valueBox = new JComboBox(new ValueModel());
        assign = new JButton(ASSIGN_ALL);
        assign.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String value = getChosenValue();
                int[] selectedRows = table.getSelectedRows();
                if (selectedRows.length > 0) {
                    for (int row : selectedRows) {
                        table.getModel().setValueAt(value, row, 1);
                    }
                }
                else {
                    for (int row = 0; row < table.getModel().getRowCount(); row++) {
                        table.getModel().setValueAt(value, row, 1);
                    }
                }
                patternField.setText("");
                patternField.requestFocus();
                timer.restart();
            }
        });
        JPanel p = new JPanel();
        p.setBorder(BorderFactory.createTitledBorder("Assign"));
        p.add(valueBox);
        p.add(assign);
        return p;
    }

    private String getChosenValue() {
        String value = (String) valueBox.getSelectedItem();
        if (value.equals(COPY_VERBATIM)) {
            value = "";
        }
        return value;
    }

    private JTable createTable() {
        table = new JTable(mapModel, createTableColumnModel());
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                int[] selectedRows = table.getSelectedRows();
                assign.setText(selectedRows.length > 0 ? ASSIGN_SELECTED : ASSIGN_ALL);
            }
        });
        return table;
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
            dialog.setVisible(false);
            dialog.dispose();
            finishedRunnable.run();
        }
    }

    private TableColumnModel createTableColumnModel() {
        TableColumn left = new TableColumn(0, 60);
        left.setWidth(400);
        left.setMinWidth(100);
        TableColumn right = new TableColumn(1, 60);
        right.setWidth(400);
        right.setMinWidth(100);
        right.setCellRenderer(new Renderer());
        DefaultTableColumnModel tcm = new DefaultTableColumnModel();
        tcm.addColumn(left);
        tcm.addColumn(right);
        return tcm;
    }

    private class MapModel extends AbstractTableModel {
        private List<String[]> rows = new ArrayList<String[]>();
        private Map<Integer, Integer> index;

        private MapModel() {
            for (Map.Entry<String, String> entry : fieldMapping.dictionary.entrySet()) {
                rows.add(new String[]{entry.getKey(), entry.getValue()});
            }
            setPattern("", false);
        }

        public void setPattern(String pattern, boolean showSetValues) {
            String sought = pattern.toLowerCase();
            Map<Integer, Integer> map = new HashMap<Integer, Integer>();
            int actual = 0, virtual = 0, assigned = 0;
            for (String[] row : rows) {
                if (!row[1].isEmpty()) {
                    assigned++;
                }
                if (row[0].toLowerCase().contains(sought) && (showSetValues || row[1].isEmpty())) {
                    map.put(virtual, actual);
                    virtual++;
                }
                actual++;
            }
            index = map;
            statusLabel.setText(String.format("Assigned: %d/%d  Visible: %d", assigned, actual, virtual));
            fireTableStructureChanged();
        }

        @Override
        public int getRowCount() {
            if (index != null) {
                return index.size();
            }
            else {
                return rows.size();
            }
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int columnIndex) {
            switch (columnIndex) {
                case 0:
                    return "Input";
                case 1:
                    return "Output";
            }
            throw new RuntimeException();
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (index != null) {
                Integer foundRow = index.get(rowIndex);
                if (foundRow != null) {
                    rowIndex = foundRow;
                }
            }
            return rows.get(rowIndex)[columnIndex];
        }

        @Override
        public void setValueAt(Object valueObject, int rowIndex, int columnIndex) {
            if (columnIndex != 1) throw new RuntimeException();
            if (valueObject == null) {
                valueObject = "";
            }
            int mappedRow = rowIndex;
            if (index != null) {
                Integer foundRow = index.get(rowIndex);
                if (foundRow != null) {
                    mappedRow = foundRow;
                }
            }
            fieldMapping.dictionary.put(rows.get(mappedRow)[0], rows.get(mappedRow)[1] = (String) valueObject);
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }

    private class Renderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column
        ) {
            if (((String) value).isEmpty()) {
                value = COPY_VERBATIM;
            }
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }

    }

    private class ValueModel extends AbstractListModel implements ComboBoxModel {
        private List<String> values = new ArrayList<String>(fieldMapping.getDefinition().validation.getOptions());
        private Object selectedItem = COPY_VERBATIM;

        private ValueModel() {
            values.add(0, COPY_VERBATIM);
        }

        @Override
        public void setSelectedItem(Object item) {
            selectedItem = item;
        }

        @Override
        public Object getSelectedItem() {
            return selectedItem;
        }

        @Override
        public int getSize() {
            return values.size();
        }

        @Override
        public Object getElementAt(int index) {
            return values.get(index);
        }
    }
}
