package eu.delving.sip;

import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.MetadataModelImpl;

import java.io.File;
import java.util.Arrays;

/**
 * Create a file store for testing
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class MockFileStoreFactory {
    public static final String SPEC = "spek";
    private String metadataPrefix = "abm";
    private File directory;
    private File specDirectory;
    private FileStore fileStore;
    private FileStore.DataSetStore dataSetStore;

    public MockFileStoreFactory() throws FileStoreException {
        File target = new File("sip-core/target");
        if (!target.exists()) {
            target = new File("target");
            if (!target.exists()) {
                throw new RuntimeException("Target directory not found");
            }
        }
        directory = new File(target, "file-store");
        if (directory.exists()) {
            delete(directory);
        }
        if (!directory.mkdirs()) {
            throw new RuntimeException("Unable to create directory " + directory.getAbsolutePath());
        }
        specDirectory = new File(directory, SPEC);
        fileStore = new FileStoreImpl(directory, getMetadataModel());
        dataSetStore = fileStore.createDataSetStore(SPEC);
    }

    public FileStore getFileStore() {
        return fileStore;
    }

    public String getMetadataPrefix() {
        return metadataPrefix;
    }

    public FileStore.DataSetStore getDataSetStore() {
        return dataSetStore;
    }

    public File getDirectory() {
        return directory;
    }

    public File getSpecDirectory() {
        return specDirectory;
    }

    public void delete() {
        delete(directory);
    }

    private void delete(File file) {
        if (file.isDirectory()) {
            for (File sub : file.listFiles()) {
                delete(sub);
            }
        }
        if (!file.delete()) {
            throw new RuntimeException(String.format("Unable to delete %s", file.getAbsolutePath()));
        }
    }

    private MetadataModel getMetadataModel() {
        try {
            MetadataModelImpl metadataModel = new MetadataModelImpl();
            metadataModel.setRecordDefinitionResources(Arrays.asList("/abm-record-definition.xml"));
            metadataModel.setDefaultPrefix(metadataPrefix);
            return metadataModel;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
