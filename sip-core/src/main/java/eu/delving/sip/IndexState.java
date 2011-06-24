/*
 * Copyright 2010 DELVING BV
 *
 *  Licensed under the EUPL, Version 1.1 or as soon they
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
 * Shared definition of IndexStates for the Services Module.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 * @author Sjoerd Siebinga <sjoerd@delving.eu>
 */

public enum IndexState {
    
    EMPTY,
    SEARCH_DISABLED,
    QUEUED,
    INDEXING,
    SEARCH_ENABLED,
    ERROR;

    public static IndexState get(String string) {
        for (IndexState t : values()) {
            if (t.toString().equalsIgnoreCase(string)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Did not recognize IndexState: [" + string + "]");
    }
}
