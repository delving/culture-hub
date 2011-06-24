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

import eu.delving.metadata.Facts;
import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.MappingModel;
import eu.delving.metadata.MetadataException;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecordMapping;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Make sure the file store is working
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class TestFileStore {
    private Logger log = Logger.getLogger(getClass());
    private MockFileStoreFactory mock;
    private FileStore fileStore;

    @Before
    public void createStore() throws FileStoreException, IOException, MetadataException {
        mock = new MockFileStoreFactory();
        fileStore = mock.getFileStore();
    }

    @After
    public void deleteStore() {
        mock.delete();
    }

    @Test
    public void createDelete() throws IOException, FileStoreException {
        FileStore.DataSetStore store = mock.getDataSetStore();
        Assert.assertEquals("Should be no files", 0, mock.getSpecDirectory().listFiles().length);
        Assert.assertFalse(store.hasSource());
        store.importFile(MockFileStoreInput.sampleFile(), null);
        Assert.assertTrue(store.hasSource());
        Assert.assertEquals("Should be one file", 1, mock.getDirectory().listFiles().length);
        Assert.assertEquals("Should be one spec", 1, fileStore.getDataSetStores().size());
        Assert.assertEquals("Should be one file", 1, mock.getSpecDirectory().listFiles().length);
        log.info("Created " + mock.getSpecDirectory().listFiles()[0].getAbsolutePath());
        InputStream inputStream = MockFileStoreInput.sampleInputStream();
        InputStream storedStream = mock.getDataSetStore().createXmlInputStream();
        int input = 0, stored;
        while (input != -1) {
            input = inputStream.read();
            stored = storedStream.read();
            Assert.assertEquals("Stream discrepancy", input, stored);
        }
        store.delete();
        Assert.assertEquals("Should be zero files", 0, mock.getDirectory().listFiles().length);
    }

    @Test
    public void manipulateAppConfig() throws FileStoreException {
        AppConfig appConfig = fileStore.getAppConfig();
        Assert.assertTrue("should be no access key", appConfig.getAccessKey().isEmpty());
        appConfig.setAccessKey("gumby");
        fileStore.setAppConfig(appConfig);
        appConfig = fileStore.getAppConfig();
        Assert.assertEquals("Should have saved access key", "gumby", appConfig.getAccessKey());
    }

    @Test
    public void manipulateMapping() throws IOException, FileStoreException, MetadataException {
        mock.getDataSetStore().importFile(MockFileStoreInput.sampleFile(), null);
        Assert.assertEquals("Spec should be the same", MockFileStoreFactory.SPEC, mock.getDataSetStore().getSpec());
        RecordMapping recordMapping = mock.getDataSetStore().getRecordMapping(mock.getMetadataPrefix());
        Assert.assertEquals("Prefixes should be the same", mock.getMetadataPrefix(), recordMapping.getPrefix());
        log.info("Mapping created with prefix " + recordMapping.getPrefix());
        MappingModel mappingModel = new MappingModel();
        mappingModel.setRecordMapping(recordMapping);
        mappingModel.setFact("/some/path", "value");
        mock.getDataSetStore().setRecordMapping(recordMapping);
        Assert.assertEquals("Should be two files", 2, mock.getSpecDirectory().listFiles().length);
        recordMapping = mock.getDataSetStore().getRecordMapping(mock.getMetadataPrefix());
        Assert.assertEquals("Should have held fact", "value", recordMapping.getFact("/some/path"));
    }

    @Test
    public void manipulateStatistics() throws IOException, FileStoreException {
        mock.getDataSetStore().importFile(MockFileStoreInput.sampleFile(), null);
        List<FieldStatistics> stats = mock.getDataSetStore().getStatistics();
        Assert.assertEquals("Should be one files", 1, mock.getSpecDirectory().listFiles().length);
        Assert.assertNull("No stats should be here", stats);
        stats = new ArrayList<FieldStatistics>();
        FieldStatistics fieldStatistics = new FieldStatistics(new Path("/stat/path"));
        fieldStatistics.recordOccurrence();
        fieldStatistics.recordValue("booger");
        fieldStatistics.finish();
        stats.add(fieldStatistics);
        mock.getDataSetStore().setStatistics(stats);
        Assert.assertEquals("Should be two files ", 2, mock.getSpecDirectory().listFiles().length);
        stats = mock.getDataSetStore().getStatistics();
        Assert.assertEquals("Should be one stat", 1, stats.size());
        Assert.assertEquals("Path discrepancy", "/stat/path", stats.get(0).getPath().toString());
    }

    @Test
    public void manipulateFacts() throws IOException, FileStoreException {
        mock.getDataSetStore().importFile(MockFileStoreInput.sampleFile(), null);
        Facts facts = mock.getDataSetStore().getFacts();
        Assert.assertEquals("facts should be empty", "", facts.get("recordRootPath"));
        facts.set("recordRootPath", "Wingy");
        mock.getDataSetStore().setFacts(facts);
        facts = mock.getDataSetStore().getFacts();
        Assert.assertEquals("facts should be restored", "Wingy", facts.get("recordRootPath"));
    }

    @Test
    public void pretendNormalize() throws IOException, FileStoreException, MetadataException {
        mock.getDataSetStore().importFile(MockFileStoreInput.sampleFile(), null);
        RecordMapping recordMapping = mock.getDataSetStore().getRecordMapping(mock.getMetadataPrefix());
        FileStore.MappingOutput mo = mock.getDataSetStore().createMappingOutput(recordMapping, null);
        mo.recordDiscarded();
        mo.recordNormalized();
        mo.recordNormalized();
        Assert.assertEquals("Should be one file", 1, mock.getSpecDirectory().listFiles().length);
        mo.close(false);
        mock.getDataSetStore().setRecordMapping(recordMapping);
        Assert.assertEquals("Should be two files", 2, mock.getSpecDirectory().listFiles().length);
        recordMapping = mock.getDataSetStore().getRecordMapping(mock.getMetadataPrefix());
        Assert.assertEquals("Mapping should contain facts", 1, recordMapping.getRecordsDiscarded());
        Assert.assertEquals("Mapping should contain facts", 2, recordMapping.getRecordsNormalized());
    }

}
