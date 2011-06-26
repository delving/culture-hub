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

package eu.delving.metadata;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/**
 * Regardless of what happens, just grab some random values as a backup in case other interesting statistics
 * don't work
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class RandomSample implements Serializable {
    private int size;
    private Set<String> values = new TreeSet<String>();

    public RandomSample(int size) {
        this.size = size;
    }

    public void recordValue(String value) {
        if (values.size() < size || Math.random() > 0.1) {
            values.add(value);
        }
        if (values.size() > size * 2) {
            Iterator<String> walk = values.iterator();
            while (walk.hasNext()) {
                walk.next();
                if (Math.random() > 0.5) {
                    walk.remove();
                }
            }
        }
    }

    public Set<String> getValues() {
        return values;
    }
}