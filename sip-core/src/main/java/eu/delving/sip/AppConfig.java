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

import com.thoughtworks.xstream.annotations.XStreamAlias;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This class
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */


@XStreamAlias("sip-creator-configuration")
public class AppConfig {
    private String serverHostPort;
    private String accessKey;
    private String recentDirectory;
    private String normalizeDirectory;
    private List<String> activeMetadataPrefixes;

    @XStreamAlias("repository-connections")
    private List<RepositoryConnection> repositoryConnections;

    public String getServerHostPort() {
        if (serverHostPort == null) {
            serverHostPort = "localhost:8983";
        }
        return serverHostPort;
    }

    public void setServerHostPort(String serverHostPort) {
        this.serverHostPort = serverHostPort;
    }

    public String getAccessKey() {
        if (accessKey == null) {
            accessKey = "";
        }
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
        saveConnection();
    }

    public void saveConnection() {
        for (RepositoryConnection connection : getRepositoryConnections()) {
            if (connection.serverHostPort.equals(getServerHostPort())) {
                connection.accessKey = getAccessKey();
                return;
            }
        }
        RepositoryConnection connection = new RepositoryConnection();
        connection.serverHostPort = getServerHostPort();
        connection.accessKey = getAccessKey();
        repositoryConnections.add(connection);
    }

    public void deleteConnection() {
        repositoryConnections = filter(getServerHostPort(), repositoryConnections);
    }

    public void selectConnection(String serverHostPort) {
        for (RepositoryConnection connection : getRepositoryConnections()) {
            if (connection.serverHostPort.equals(serverHostPort)) {
                this.serverHostPort = connection.serverHostPort;
                this.accessKey = connection.accessKey;
                return;
            }
        }
    }

    public String getRecentDirectory() {
        if (recentDirectory == null) {
            recentDirectory = System.getProperty("user.home");
        }
        return recentDirectory;
    }

    public void setRecentDirectory(String directory) {
        this.recentDirectory = directory;
    }

    public String getNormalizeDirectory() {
        if (normalizeDirectory == null) {
            normalizeDirectory = System.getProperty("user.home");
        }
        return normalizeDirectory;
    }

    public void setNormalizeDirectory(String directory) {
        this.normalizeDirectory = directory;
    }

    public void addActiveMetadataPrefix(String prefix) {
        if (!getActiveMetadataPrefixes().contains(prefix)) {
            getActiveMetadataPrefixes().add(prefix);
        }
    }

    public void removeActiveMetadataPrefix(String prefix) {
        getActiveMetadataPrefixes().remove(prefix);
    }

    public List<String> getActiveMetadataPrefixes() {
        if (activeMetadataPrefixes == null) {
            activeMetadataPrefixes = new ArrayList<String>();
        }
        return activeMetadataPrefixes;
    }

    public List<RepositoryConnection> getRepositoryConnections() {
        if (repositoryConnections == null) {
            repositoryConnections = new ArrayList<RepositoryConnection>();
        }
        return repositoryConnections;
    }

    @XStreamAlias("repository-connection")
    public static class RepositoryConnection {
        public String serverHostPort;
        public String accessKey;
    }

    private static List<RepositoryConnection> filter(String serverHostPort, List<RepositoryConnection> original) {
        List<RepositoryConnection> list = new ArrayList<RepositoryConnection>(original);
        Iterator<RepositoryConnection> walk = list.iterator();
        while (walk.hasNext()) {
            RepositoryConnection connection = walk.next();
            if (serverHostPort.equals(connection.serverHostPort)) {
                walk.remove();
            }
        }
        return list;
    }
}
