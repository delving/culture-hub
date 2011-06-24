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

import eu.delving.metadata.FieldDefinition;
import eu.delving.metadata.FieldMapping;
import eu.delving.metadata.MappingModel;
import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.RecordMapping;

import javax.swing.AbstractListModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.ListModel;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * todo
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class FieldListModel extends AbstractListModel {
    private MetadataModel metadataModel;
    private List<FieldDefinition> fieldDefinitions;
    private Unmapped unmapped;

    public FieldListModel(MetadataModel metadataModel) {
        this.metadataModel = metadataModel;
        this.fieldDefinitions = new ArrayList<FieldDefinition>();
    }

    public ListModel getUnmapped(MappingModel mappingModel) {
        if (unmapped == null) {
            mappingModel.addListener(unmapped = new Unmapped());
        }
        return unmapped;
    }

    @Override
    public int getSize() {
        return fieldDefinitions.size();
    }

    @Override
    public Object getElementAt(int index) {
        return fieldDefinitions.get(index);
    }

    public static class CellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            FieldDefinition fieldDefinition = (FieldDefinition) value;
            String string = fieldDefinition.getFieldNameString();
            FieldDefinition.Validation validation = fieldDefinition.validation;
            if (validation != null && validation.requiredGroup != null) {
                string += " (required: " + validation.requiredGroup + ")";
            }
            return super.getListCellRendererComponent(list, string, index, isSelected, cellHasFocus);
        }
    }

    public class Unmapped extends AbstractListModel implements MappingModel.Listener {
        private List<FieldDefinition> unmappedFields = new ArrayList<FieldDefinition>();

        @Override
        public int getSize() {
            return unmappedFields.size();
        }

        @Override
        public Object getElementAt(int index) {
            return unmappedFields.get(index);
        }

        @Override
        public void mappingChanged(RecordMapping recordMapping) {
            int sizeBefore = getSize();
            unmappedFields.clear();
            fieldDefinitions.clear();
            fireIntervalRemoved(this, 0, sizeBefore);
            if (recordMapping != null) {
                fieldDefinitions.addAll(metadataModel.getRecordDefinition(recordMapping.getPrefix()).getMappableFields());
                Collections.sort(fieldDefinitions, new Comparator<FieldDefinition>() {
                    @Override
                    public int compare(FieldDefinition field0, FieldDefinition field1) {
                        return field0.getFieldNameString().compareTo(field1.getFieldNameString());
                    }
                });
                nextVariable:
                for (FieldDefinition fieldDefinition : fieldDefinitions) {
                    for (FieldMapping fieldMapping : recordMapping.getFieldMappings()) {
                        if (fieldMapping.fieldDefinition == fieldDefinition) {
                            continue nextVariable;
                        }
                    }
                    unmappedFields.add(fieldDefinition);
                }
                fireIntervalAdded(this, 0, getSize());
            }
        }
    }
}
