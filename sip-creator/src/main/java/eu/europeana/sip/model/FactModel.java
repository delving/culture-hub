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

package eu.europeana.sip.model;

import eu.delving.metadata.FactDefinition;
import eu.delving.metadata.Facts;
import eu.delving.metadata.RecordMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * All facts needed to define the constant things definining the data set, as well as
 * some of the constants used by the mapping
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class FactModel {
    private Map<String, String> factMap = new LinkedHashMap<String, String>();

    public void setFacts(Facts facts, String spec) {
        boolean changed = false;
        for (FactDefinition factDefinition : Facts.definitions()) {
            String oldValue = get(factDefinition);
            String newValue = facts.get(factDefinition.name);
            if ("spec".equals(factDefinition.name)) {
                newValue = spec;
            }
            if (!oldValue.equals(newValue)) {
                if (put(factDefinition, newValue)) {
                    changed = true;
                }
            }
        }
        if (changed) {
            for (Listener listener : listeners) {
                listener.updatedFact(this, true);
            }
        }
    }

    public boolean fillFacts(Facts facts) {
        boolean changed = false;
        if (facts != null) {
            for (FactDefinition factDefinition : Facts.definitions()) {
                if (facts.set(factDefinition.name, get(factDefinition))) {
                    changed = true;
                }
            }
        }
        return changed;
    }

    public boolean fillRecordMapping(RecordMapping recordMapping) {
        boolean changed = false;
        for (FactDefinition factDefinition : Facts.definitions()) {
            if (recordMapping.setFact(factDefinition.name, get(factDefinition))) {
                changed = true;
            }
        }
        return changed;
    }

    public void set(FactDefinition factDefinition, String value) {
        if (put(factDefinition, value)) {
            for (Listener listener : listeners) {
                listener.updatedFact(this, true);
            }
        }
    }

    public void clear() {
        boolean changed = false;
        for (FactDefinition factDefinition : Facts.definitions()) {
            if (put(factDefinition, factDefinition.defaultValue == null ? "" : factDefinition.defaultValue)) {
                changed = true;
            }
        }
        if (changed) {
            for (Listener listener : listeners) {
                listener.updatedFact(this, false);
            }
        }
    }

    public String get(FactDefinition factDefinition) {
        String value = factMap.get(factDefinition.name);
        if (value == null) {
            factMap.put(factDefinition.name, value = "");
        }
        return value;
    }

    private boolean put(FactDefinition factDefinition, String value) {
        String existing = get(factDefinition);
        if (value.equals(existing)) {
            return false;
        }
        if (value.isEmpty() && factDefinition.defaultValue != null) {
            value = factDefinition.defaultValue;
        }
        factMap.put(factDefinition.name, value);
        return true;
    }

    private List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public interface Listener {
        void updatedFact(FactModel factModel, boolean interactive);
    }

}
