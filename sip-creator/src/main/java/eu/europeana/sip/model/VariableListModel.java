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

import eu.delving.metadata.AnalysisTree;
import eu.delving.metadata.FieldMapping;
import eu.delving.metadata.MappingModel;
import eu.delving.metadata.RecordMapping;
import eu.delving.metadata.SourceVariable;

import javax.swing.AbstractListModel;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Given an annotation processor, provide food for the JList to show fields
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class VariableListModel extends AbstractListModel {
    private List<SourceVariable> variableList = new ArrayList<SourceVariable>();
    private WithCounts withCounts;

    public void setVariableList(List<AnalysisTree.Node> variableList) {
        clear();
        for (AnalysisTree.Node node : variableList) {
            this.variableList.add(new SourceVariable(node));
        }
        Collections.sort(this.variableList);
        fireIntervalAdded(this, 0, getSize());
    }

    public void clear() {
        int size = getSize();
        if (size > 0) {
            this.variableList.clear();
            fireIntervalRemoved(this, 0, size);
        }
    }

    @Override
    public int getSize() {
        return variableList.size();
    }

    @Override
    public Object getElementAt(int index) {
        return variableList.get(index);
    }

    public ListModel getWithCounts(MappingModel mappingModel) {
        if (withCounts == null) {
            withCounts = new WithCounts(mappingModel);
            this.addListDataListener(withCounts);
        }
        return withCounts;
    }

    public class WithCounts extends AbstractListModel implements ListDataListener, MappingModel.Listener {
        private MappingModel mappingModel;
        private List<SourceVariable> sourceVariables = new ArrayList<SourceVariable>();

        public WithCounts(MappingModel mappingModel) {
            this.mappingModel = mappingModel;
            this.mappingModel.addListener(this);
        }

        @Override
        public int getSize() {
            return sourceVariables.size();
        }

        @Override
        public Object getElementAt(int index) {
            return sourceVariables.get(index);
        }

        public void refresh() {
            int sizeBefore = getSize();
            sourceVariables.clear();
            fireIntervalRemoved(this, 0, sizeBefore);
            RecordMapping recordMapping = mappingModel.getRecordMapping();
            if (recordMapping != null) {
                for (SourceVariable uncountedHolder : variableList) {
                    SourceVariable sourceVariable = new SourceVariable(uncountedHolder.getNode());
                    for (FieldMapping fieldMapping : mappingModel.getRecordMapping().getFieldMappings()) {
                        for (String variable : fieldMapping.getVariableNames()) {
                            sourceVariable.checkIfMapped(variable);
                        }
                    }
                    sourceVariables.add(sourceVariable);
                }
                Collections.sort(sourceVariables);
                fireIntervalAdded(this, 0, getSize());
            }
        }

        @Override
        public void intervalAdded(ListDataEvent listDataEvent) {
            refresh();
        }

        @Override
        public void intervalRemoved(ListDataEvent listDataEvent) {
            refresh();
        }

        @Override
        public void contentsChanged(ListDataEvent listDataEvent) {
            refresh();
        }

        @Override
        public void mappingChanged(RecordMapping recordMapping) {
            refresh();
        }
    }
}