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
import eu.delving.metadata.RecordMapping;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

/**
 * This interface describes how files are stored by the sip-creator
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public interface FileStore {

    AppConfig getAppConfig() throws FileStoreException;

    void setAppConfig(AppConfig appConfig) throws FileStoreException;

    String getCode(String fileName) throws FileStoreException;

    void setCode(String fileName, String code) throws FileStoreException;

    void setTemplate(String name, RecordMapping recordMapping) throws FileStoreException;

    Map<String, RecordMapping> getTemplates();

    void deleteTemplate(String name);

    Map<String, DataSetStore> getDataSetStores();

    DataSetStore createDataSetStore(String spec) throws FileStoreException;

    public interface DataSetStore {

        String getSpec();

        boolean hasSource();

        Facts getFacts();

        void importFile(File inputFile, ProgressListener progressListener) throws FileStoreException;

        void clearSource() throws FileStoreException;

        InputStream createXmlInputStream() throws FileStoreException;

        List<FieldStatistics> getStatistics();

        void setStatistics(List<FieldStatistics> fieldStatisticsList) throws FileStoreException;

        RecordMapping getRecordMapping(String metadataPrefix) throws FileStoreException;

        void setRecordMapping(RecordMapping recordMapping) throws FileStoreException;

        void setFacts(Facts facts) throws FileStoreException;

        MappingOutput createMappingOutput(RecordMapping recordMapping, File normalizedDirectory) throws FileStoreException;

        void delete() throws FileStoreException;

        File getFactsFile();

        File getSourceFile();

        File getMappingFile(String metadataPrefix);

        List<String> getMappingPrefixes();

        void acceptSipZip(ZipInputStream zipInputStream, ProgressListener progressListener) throws IOException, FileStoreException;
    }

    public interface MappingOutput {

        Writer getOutputWriter();

        Writer getDiscardedWriter();

        void recordNormalized();

        void recordDiscarded();

        void close(boolean abort) throws FileStoreException;
    }

    String APP_CONFIG_FILE_NAME = "app-config.xml";
    String SOURCE_FILE_NAME = "source.xml.gz";
    String STATISTICS_FILE_NAME = "statistics.ser";
    String FACTS_FILE_NAME = "facts.txt";
    String MAPPING_FILE_PATTERN = "mapping_%s.xml";
    String MAPPING_FILE_PREFIX = "mapping_";
    String MAPPING_FILE_SUFFIX = ".xml";
    String RECORD_ANALYSIS_FILE_NAME = "RecordAnalysis.groovy";
}
