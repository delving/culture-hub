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

package eu.europeana.sip.gui;

import eu.delving.sip.FileStore;
import eu.delving.sip.FileStoreException;
import eu.delving.sip.ProgressListener;
import eu.europeana.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.ProgressMonitor;
import javax.swing.filechooser.FileFilter;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.Map;

/**
 * The menu for handling files
 *
 * @author Gerald de Jong, Beautiful Code BV, <geralddejong@gmail.com>
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */

public class ImportMenu extends JMenu {
    private Component parent;
    private SipModel sipModel;
    private Runnable dataStoreCreated;

    public ImportMenu(Component parent, SipModel sipModel, Runnable dataStoreCreated) {
        super("Import");
        this.parent = parent;
        this.sipModel = sipModel;
        this.dataStoreCreated = dataStoreCreated;
        refresh();
    }

    private class LoadNewFileAction extends AbstractAction {
        private JFileChooser chooser = new JFileChooser("XML File");

        private LoadNewFileAction(File directory) {
            super("From " + directory.getAbsolutePath());
            chooser.setCurrentDirectory(directory);
            chooser.setFileFilter(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return file.isFile() && file.getName().endsWith(".xml") || file.getName().endsWith(".xml.gz");
                }

                @Override
                public String getDescription() {
                    return "XML or GZIP/XML Files";
                }
            });
            chooser.setMultiSelectionEnabled(false);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            int choiceMade = chooser.showOpenDialog(parent);
            if (choiceMade == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                sipModel.getAppConfigModel().setRecentDirectory(file);
                selectInputFile(file);
                refresh();
            }
        }
    }

    public boolean selectInputFile(File file) {
        if (!file.exists()) {
            return false;
        }
        Map<String, FileStore.DataSetStore> dataSetStores = sipModel.getFileStore().getDataSetStores();
        Object[] specs = new Object[dataSetStores.keySet().size() + 1];
        int index = 0;
        specs[index++] = "<New Data Set>";
        for (String key : dataSetStores.keySet()) {
            specs[index++] = key;
        }
        String spec = (String) JOptionPane.showInputDialog(
                parent,
                String.format(
                        "<html>Please choose the Data Set into which<br><br>" +
                                "<pre><strong>%s</strong></pre><br>" +
                                "will be imported.  You may either choose an existing one<br>" +
                                "or create a new one.<br><br>",
                        file.getAbsolutePath()
                ),
                "Existing Data Set",
                JOptionPane.PLAIN_MESSAGE,
                null,
                specs,
                ""
        );
        if (spec == null) {
            return false;
        }
        if (spec.startsWith("<")) {
            spec = JOptionPane.showInputDialog(
                    parent,
                    String.format(
                            "<html>You have selected the following file for importing:<br><br>" +
                                    "<pre><strong>%s</strong></pre><br>" +
                                    "To complete the import you must enter a Data Set Spec name which will serve<br>" +
                                    "to identify it in the future. For consistency this cannot be changed later, so choose<br>" +
                                    "carefully.<br><br>",
                            file.getAbsolutePath()
                    ),
                    "Select and Enter Data Set Spec",
                    JOptionPane.QUESTION_MESSAGE
            );
        }
        if (spec == null || spec.trim().isEmpty()) {
            return false;
        }
        int doImport = JOptionPane.showConfirmDialog(
                parent,
                String.format(
                        "<html>Are you sure you wish to import this file<br><br>" +
                                "<pre><strong>%s</strong></pre><br>" +
                                "as a Data Set called '<strong>%s</strong>'?<br><br>",
                        file.getAbsolutePath(),
                        spec
                ),
                "Verify your choice",
                JOptionPane.YES_NO_OPTION
        );
        if (doImport == JOptionPane.YES_OPTION) {
            ProgressMonitor progressMonitor = new ProgressMonitor(parent, "Importing", "Storing data for " + spec, 0, 100);
            try {
                FileStore.DataSetStore store = dataSetStores.containsKey(spec) ? sipModel.getFileStore().getDataSetStores().get(spec) : sipModel.getFileStore().createDataSetStore(spec);
                if (store.hasSource()) {
                    store.clearSource();
                }
                sipModel.createDataSetStore(store, file, new ProgressListener.Adapter(progressMonitor) {
                    @Override
                    public void swingFinished(boolean success) {
                        if (success) {
                            dataStoreCreated.run();
                        }
                    }
                });
                return true;
            }
            catch (FileStoreException e) {
                sipModel.getUserNotifier().tellUser("Unable to import", e);
            }
        }
        return false;
    }

    private void refresh() {
        removeAll();
        File directory = new File(sipModel.getAppConfigModel().getRecentDirectory());
        while (directory != null) {
            add(new LoadNewFileAction(directory));
            directory = directory.getParentFile();
        }
    }
}
