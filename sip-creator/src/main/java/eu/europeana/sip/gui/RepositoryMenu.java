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

package eu.europeana.sip.gui;

import eu.delving.sip.AppConfig;
import eu.europeana.sip.model.AppConfigModel;
import eu.europeana.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * @author Gerald de Jong, Delving BV <gerald@delving.eu>
 */

public class RepositoryMenu extends JMenu {
    private Component parent;
    private SipModel sipModel;

    public RepositoryMenu(Component parent, SipModel sipModel) {
        super("Repository");
        this.parent = parent;
        this.sipModel = sipModel;
        sipModel.getAppConfigModel().addListener(new AppConfigModel.Listener() {
            @Override
            public void appConfigUpdated(AppConfig appConfig) {
                refresh();
            }
        });
        refresh();
    }

    private void refresh() {
        removeAll();
        add(new ServerHostPortAction());
        add(new AccessKeyAction());
        addSeparator();
        add(new SaveAction());
        add(new DeleteAction());
        List<AppConfig.RepositoryConnection> connections = sipModel.getAppConfigModel().getRepositoryConnections();
        if (!connections.isEmpty()) {
            JMenu selectMenu = new JMenu("Select");
            add(selectMenu);
            for (AppConfig.RepositoryConnection connection : connections) {
                Action action = new SelectAction(connection.serverHostPort);
                selectMenu.add(action);
                if (connection.serverHostPort.equals(sipModel.getAppConfigModel().getServerHostPort())) {
                    action.setEnabled(false);
                }
            }
        }
    }

    private class ServerHostPortAction extends AbstractAction {

        public ServerHostPortAction() {
            super("Server Network Address");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            String serverHostPort = JOptionPane.showInputDialog(parent, "Server network address host:port (eg. delving.eu:8080).", sipModel.getAppConfigModel().getServerHostPort());
            if (serverHostPort != null && !serverHostPort.isEmpty()) {
                sipModel.getAppConfigModel().setServerHostPort(serverHostPort);
            }
        }
    }

    private class AccessKeyAction extends AbstractAction {

        public AccessKeyAction() {
            super("Access Key");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            JPasswordField passwordField = new JPasswordField(sipModel.getAppConfigModel().getAccessKey(), 35);
            Object[] msg = {"Server Access Key", passwordField};
            int result = JOptionPane.showConfirmDialog(parent, msg, "Permission", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                sipModel.getAppConfigModel().setServerAccessKey(new String(passwordField.getPassword()));
            }
        }
    }

    private class SaveAction extends AbstractAction {

        public SaveAction() {
            super("Save");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            sipModel.getAppConfigModel().saveConnection();
        }
    }

    private class DeleteAction extends AbstractAction {

        public DeleteAction() {
            super("Delete");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            sipModel.getAppConfigModel().deleteConnection();
        }
    }

    private class SelectAction extends AbstractAction {
        private String serverHostPort;

        public SelectAction(String serverHostPort) {
            super(serverHostPort);
            this.serverHostPort = serverHostPort;
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            sipModel.getAppConfigModel().selectConnection(serverHostPort);
        }
    }

}