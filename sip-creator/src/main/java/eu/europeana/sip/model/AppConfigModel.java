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

import eu.delving.sip.AppConfig;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class AppConfigModel {
    private AppConfig appConfig;
    private List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    public interface Listener {
        void appConfigUpdated(AppConfig appConfig);
    }

    public AppConfigModel(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public String getServerHostPort() {
        return appConfig.getServerHostPort();
    }

    public void setServerHostPort(String hostPort) {
        appConfig.setServerHostPort(hostPort);
        fireChangeEvent();
    }

    public String getServerUrl() {
        return String.format("http://%s/services/dataset", appConfig.getServerHostPort());
    }

    public String getAccessKey() {
        return appConfig.getAccessKey();
    }

    public void setServerAccessKey(String key) {
        appConfig.setAccessKey(key);
        fireChangeEvent();
    }

    public void saveConnection() {
        appConfig.saveConnection();
        fireChangeEvent();
    }

    public void deleteConnection() {
        appConfig.deleteConnection();
        fireChangeEvent();
    }

    public void selectConnection(String serverHostPort) {
        appConfig.selectConnection(serverHostPort);
        fireChangeEvent();
    }

    public List<AppConfig.RepositoryConnection> getRepositoryConnections() {
        return appConfig.getRepositoryConnections();
    }

    public String getRecentDirectory() {
        return appConfig.getRecentDirectory();
    }

    public void setRecentDirectory(File directory) {
        if (!directory.isDirectory()) {
            directory = directory.getParentFile();
        }
        appConfig.setRecentDirectory(directory.getAbsolutePath());
        fireChangeEvent();
    }

    public String getNormalizeDirectory() {
        return appConfig.getNormalizeDirectory();
    }

    public void setNormalizeDirectory(File directory) {
        if (!directory.isDirectory()) {
            directory = directory.getParentFile();
        }
        appConfig.setNormalizeDirectory(directory.getAbsolutePath());
        fireChangeEvent();
    }

    public List<String> getActiveMetadataPrefixes() {
        return appConfig.getActiveMetadataPrefixes();
    }

    public void setMetadataPrefixActive(String prefix, boolean state) {
        if (state) {
            appConfig.addActiveMetadataPrefix(prefix);
        }
        else {
            appConfig.removeActiveMetadataPrefix(prefix);
        }
        fireChangeEvent();
    }

    private void fireChangeEvent() {
        for (Listener listener : listeners) {
            listener.appConfigUpdated(appConfig);
        }
    }
}
