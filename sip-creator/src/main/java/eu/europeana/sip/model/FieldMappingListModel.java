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

import eu.delving.metadata.FieldMapping;
import eu.delving.metadata.MappingModel;
import eu.delving.metadata.RecordMapping;

import javax.swing.AbstractListModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * A ListModel of FieldMapping instances
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class FieldMappingListModel extends AbstractListModel implements MappingModel.Listener {
    private List<FieldMapping> list = new ArrayList<FieldMapping>();

    @Override
    public int getSize() {
        return list.size();
    }

    @Override
    public Object getElementAt(int index) {
        return list.get(index);
    }

    @Override
    public void mappingChanged(RecordMapping recordMapping) {
        clear();
        if (recordMapping != null) {
            for (FieldMapping fieldMapping : recordMapping.getFieldMappings()) {
                list.add(fieldMapping);
            }
            fireIntervalAdded(this, 0, getSize());
        }
    }

    private void clear() {
        int size = getSize();
        if (size > 0) {
            this.list.clear();
            fireIntervalRemoved(this, 0, size);
        }
    }

    public static class CellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            FieldMapping fieldMapping = (FieldMapping) value;
            return super.getListCellRendererComponent(list, fieldMapping.getDescription(), index, isSelected, cellHasFocus);
        }
    }


}