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

package eu.delving.sip;

/**
 * Shared definition of state
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public enum DataSetState {

    INCOMPLETE,
    DISABLED,
    UPLOADED,
    QUEUED,
    INDEXING,
    ENABLED,
    ERROR;

    public static DataSetState get(String string) {
        for (DataSetState t : values()) {
            if (t.toString().equalsIgnoreCase(string)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Did not recognize DataSetState: [" + string + "]");
    }
}
