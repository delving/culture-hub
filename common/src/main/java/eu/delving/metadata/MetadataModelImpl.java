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

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Implementing the MetadataModel inteface
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class MetadataModelImpl implements MetadataModel {

    private Map<String, RecordDefinition> recordDefinitions = new TreeMap<String, RecordDefinition>();
    private String defaultPrefix;

    public MetadataModelImpl() {
    }

    public MetadataModelImpl(String path, String prefix) {
            try {
                setRecordDefinitionResources(Arrays.asList(new String[]{path}));
                setDefaultPrefix(prefix);
            }
            catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
    }

    public void setRecordDefinitionResources(List<String> paths) throws IOException, MetadataException {
        for (String path : paths) {
            URL url = getClass().getResource(path);
            RecordDefinition recordDefinition = RecordDefinition.read(url.openStream());
            recordDefinitions.put(recordDefinition.prefix, recordDefinition);
        }
    }

    public void setDefaultPrefix(String defaultPrefix) {
        this.defaultPrefix = defaultPrefix;
    }

    @Override
    public RecordDefinition getRecordDefinition() {
        return getRecordDefinition(defaultPrefix);
    }

    @Override
    public Set<String> getPrefixes() {
        return recordDefinitions.keySet();
    }

    @Override
    public RecordDefinition getRecordDefinition(String prefix) {
        RecordDefinition definition = recordDefinitions.get(prefix);
        if (definition == null) {
            throw new RuntimeException("Expected to find a record definition for prefix " + prefix);
        }
        return definition;
    }
}
