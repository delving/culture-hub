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

import com.thoughtworks.xstream.XStream;
import eu.delving.metadata.Facts;
import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.Hasher;
import eu.delving.metadata.MetadataException;
import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.RecordDefinition;
import eu.delving.metadata.RecordMapping;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * This interface describes how files are stored by the sip-creator
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class FileStoreImpl implements FileStore {

    private File home;
    private MetadataModel metadataModel;
    public static final int BLOCK_SIZE = 4096;
    public static final int MAX_HASH_HISTORY = 3;

    public FileStoreImpl(File home, MetadataModel metadataModel) throws FileStoreException {
        this.home = home;
        this.metadataModel = metadataModel;
        if (!home.exists()) {
            if (!home.mkdirs()) {
                throw new FileStoreException(String.format("Unable to create file store in %s", home.getAbsolutePath()));
            }
        }
    }

    @Override
    public AppConfig getAppConfig() throws FileStoreException {
        File appConfigFile = new File(home, APP_CONFIG_FILE_NAME);
        AppConfig config = null;
        if (appConfigFile.exists()) {
            try {
                Reader reader = new InputStreamReader(new FileInputStream(appConfigFile));
                config = (AppConfig) getAppConfigStream().fromXML(reader);
                reader.close();
            }
            catch (Exception e) {
                throw new FileStoreException(String.format("Unable to read application configuration from %s", appConfigFile.getAbsolutePath()));
            }
        }
        if (config == null) {
            config = new AppConfig();
        }
        return config;
    }

    @Override
    public void setAppConfig(AppConfig appConfig) throws FileStoreException {
        File appConfigFile = new File(home, APP_CONFIG_FILE_NAME);
        try {
            Writer writer = new OutputStreamWriter(new FileOutputStream(appConfigFile), "UTF-8");
            getAppConfigStream().toXML(appConfig, writer);
            writer.close();
        }
        catch (IOException e) {
            throw new FileStoreException(String.format("Unable to save application config to %s", appConfigFile.getAbsolutePath()), e);
        }
    }

    @Override
    public String getCode(String fileName) throws FileStoreException {
        File codeFile = new File(home, fileName);
        try {
            if (codeFile.exists()) {
                return readFileCode(codeFile);
            }
            else {
                return readResourceCode(fileName);
            }
        }
        catch (IOException e) {
            throw new FileStoreException("Unable to get code " + fileName, e);
        }
    }

    @Override
    public void setCode(String fileName, String code) throws FileStoreException {
        File codeFile = new File(home, fileName);
        try {
            writeCode(codeFile, code);
        }
        catch (IOException e) {
            throw new FileStoreException("Unable to set code " + fileName, e);
        }
    }

    @Override
    public void setTemplate(String name, RecordMapping recordMapping) throws FileStoreException {
        File templateFile = new File(home, String.format(MAPPING_FILE_PATTERN, name));
        try {
            FileOutputStream fos = new FileOutputStream(templateFile);
            RecordMapping.write(recordMapping, fos);
            fos.close();
        }
        catch (IOException e) {
            throw new FileStoreException(String.format("Unable to save template to %s", templateFile.getAbsolutePath()), e);
        }
    }

    @Override
    public Map<String, RecordMapping> getTemplates() {
        Map<String, RecordMapping> templates = new TreeMap<String, RecordMapping>();
        for (File templateFile : home.listFiles(new MappingFileFilter())) {
            try {
                FileInputStream fis = new FileInputStream(templateFile);
                RecordMapping recordMapping = RecordMapping.read(fis, metadataModel);
                fis.close();
                String name = templateFile.getName();
                name = name.substring(MAPPING_FILE_PREFIX.length());
                name = name.substring(0, name.length() - MAPPING_FILE_SUFFIX.length());
                templates.put(name, recordMapping);
            }
            catch (Exception e) {
                templateFile.delete();
            }
        }
        return templates;
    }

    @Override
    public void deleteTemplate(String name) {
        File templateFile = new File(home, String.format(MAPPING_FILE_PATTERN, name));
        if (templateFile.exists()) {
            templateFile.delete();
        }
    }

    @Override
    public Map<String, DataSetStore> getDataSetStores() {
        Map<String, DataSetStore> map = new TreeMap<String, DataSetStore>();
        File[] list = home.listFiles();
        if (list != null) {
            for (File file : list) {
                if (file.isDirectory()) {
                    map.put(file.getName(), new DataSetStoreImpl(file));
                }
            }
        }
        return map;
    }

    @Override
    public DataSetStore createDataSetStore(String spec) throws FileStoreException {
        File directory = new File(home, spec);
        if (directory.exists()) {
            throw new FileStoreException(String.format("Data store directory %s already exists", directory.getAbsolutePath()));
        }
        if (!directory.mkdirs()) {
            throw new FileStoreException(String.format("Unable to create data store directory %s", directory.getAbsolutePath()));
        }
        return new DataSetStoreImpl(directory);
    }

    public class DataSetStoreImpl implements DataSetStore {

        private File directory;

        public DataSetStoreImpl(File directory) {
            this.directory = directory;
        }

        @Override
        public String getSpec() {
            return directory.getName();
        }

        @Override
        public boolean hasSource() {
            return directory.listFiles(new SourceFileFilter()).length > 0;
        }

        @Override
        public Facts getFacts() {
            File factsFile = getFactsFile();
            Facts facts = null;
            if (factsFile.exists()) {
                try {
                    facts = Facts.read(new FileInputStream(factsFile));
                }
                catch (Exception e) {
                    // eat this exception
                }
            }
            if (facts == null) {
                facts = new Facts();
            }
            return facts;
        }


        @Override
        public void importFile(File inputFile, ProgressListener progressListener) throws FileStoreException {
            int fileBlocks = (int) (inputFile.length() / BLOCK_SIZE);
            if (progressListener != null) progressListener.setTotal(fileBlocks);
            File source = new File(directory, SOURCE_FILE_NAME);
            Hasher hasher = new Hasher();
            boolean cancelled = false;
            try {
                InputStream inputStream;
                if (inputFile.getName().endsWith(".xml")) {
                    inputStream = new FileInputStream(inputFile);
                }
                else if (inputFile.getName().endsWith(".xml.gz")) {
                    inputStream = new GZIPInputStream(new FileInputStream(inputFile));
                }
                else {
                    throw new IllegalArgumentException("Input file should be .xml or .xml.gz, but it is " + inputFile.getName());
                }
                OutputStream gzipOutputStream = new GZIPOutputStream(new FileOutputStream(source));
                byte[] buffer = new byte[BLOCK_SIZE];
                long totalBytesRead = 0;
                int bytesRead;
                while (-1 != (bytesRead = inputStream.read(buffer))) {
                    gzipOutputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    if (progressListener != null) {
                        if (!progressListener.setProgress((int) (totalBytesRead / BLOCK_SIZE))) {
                            cancelled = true;
                            break;
                        }
                    }
                    hasher.update(buffer, bytesRead);
                }
                if (progressListener != null) progressListener.finished(!cancelled);
                inputStream.close();
                gzipOutputStream.close();
            }
            catch (Exception e) {
                if (progressListener != null) progressListener.finished(false);
                throw new FileStoreException("Unable to capture XML input into " + source.getAbsolutePath(), e);
            }
            if (cancelled) {
                if (!source.delete()) {
                    throw new FileStoreException("Unable to delete " + source.getAbsolutePath());
                }
                clearSource();
            }
            else {
                String hash = hasher.toString();
                File hashedSource = new File(directory, hash + "__" + SOURCE_FILE_NAME);
                if (!source.renameTo(hashedSource)) {
                    throw new FileStoreException(String.format("Unable to rename %s to %s", source.getAbsolutePath(), hashedSource.getAbsolutePath()));
                }
                Facts facts = getFacts();
                if (facts.isDownloadedSource()) {
                    facts.setDownloadedSource(false);
                    setFacts(facts);
                }
                File statisticsFile = new File(directory, STATISTICS_FILE_NAME);
                if (statisticsFile.exists()) {
                    delete();
                }
            }
        }

        @Override
        public void clearSource() throws FileStoreException {
            File[] sources = directory.listFiles(new SourceFileFilter());
            for (File sourceFile : sources) {
                if (!sourceFile.delete()) {
                    throw new FileStoreException(String.format("Unable to delete %s", sourceFile.getAbsolutePath()));
                }
            }
            File statisticsFile = new File(directory, STATISTICS_FILE_NAME);
            if (statisticsFile.exists()) {
                if (!statisticsFile.delete()) {
                    throw new FileStoreException("Unable to delete statistics file.");
                }
            }
        }

        @Override
        public InputStream createXmlInputStream() throws FileStoreException {
            File source = getSourceFile();
            try {
                return new GZIPInputStream(new FileInputStream(source));
            }
            catch (IOException e) {
                throw new FileStoreException(String.format("Unable to create input stream from %s", source.getAbsolutePath()), e);
            }
        }

        @Override
        public List<FieldStatistics> getStatistics() {
            File statisticsFile = new File(directory, STATISTICS_FILE_NAME);
            if (statisticsFile.exists()) {
                try {
                    ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(statisticsFile)));
                    @SuppressWarnings("unchecked")
                    List<FieldStatistics> fieldStatisticsList = (List<FieldStatistics>) in.readObject();
                    in.close();
                    return fieldStatisticsList;
                }
                catch (Exception e) {
                    statisticsFile.delete();
                }
            }
            return null;
        }

        @Override
        public void setStatistics(List<FieldStatistics> fieldStatisticsList) throws FileStoreException {
            File statisticsFile = new File(directory, STATISTICS_FILE_NAME);
            try {
                ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(statisticsFile)));
                out.writeObject(fieldStatisticsList);
                out.close();
            }
            catch (IOException e) {
                throw new FileStoreException(String.format("Unable to save statistics file to %s", statisticsFile.getAbsolutePath()), e);
            }
        }

        @Override
        public RecordMapping getRecordMapping(String metadataPrefix) throws FileStoreException {
            RecordDefinition recordDefinition = metadataModel.getRecordDefinition(metadataPrefix);
            File mappingFile = findMappingFile(directory, metadataPrefix);
            if (mappingFile.exists()) {
                try {
                    FileInputStream is = new FileInputStream(mappingFile);
                    return RecordMapping.read(is, metadataModel);
                }
                catch (Exception e) {
                    throw new FileStoreException(String.format("Unable to read mapping from %s", mappingFile.getAbsolutePath()), e);
                }
            }
            else {
                return new RecordMapping(recordDefinition.prefix);
            }
        }

        @Override
        public void setRecordMapping(RecordMapping recordMapping) throws FileStoreException {
            File mappingFile = new File(directory, String.format(MAPPING_FILE_PATTERN, recordMapping.getPrefix()));
            try {
                FileOutputStream out = new FileOutputStream(mappingFile);
                RecordMapping.write(recordMapping, out);
                out.close();
            }
            catch (IOException e) {
                throw new FileStoreException(String.format("Unable to save mapping to %s", mappingFile.getAbsolutePath()), e);
            }
        }

        @Override
        public void setFacts(Facts facts) throws FileStoreException {
            File factsFile = new File(directory, FACTS_FILE_NAME);
            try {
                Facts.write(facts, new FileOutputStream(factsFile));
            }
            catch (IOException e) {
                throw new FileStoreException(String.format("Unable to save facts to %s", factsFile.getAbsolutePath()), e);
            }
            catch (MetadataException e) {
                throw new FileStoreException("Unable to set facts", e);
            }
        }

        @Override
        public MappingOutput createMappingOutput(RecordMapping recordMapping, File normalizedDirectory) throws FileStoreException {
            return new MappingOutputImpl(directory.getName(), recordMapping, normalizedDirectory);
        }

        @Override
        public void delete() throws FileStoreException {
            delete(directory);
        }

        @Override
        public File getFactsFile() {
            return findFactsFile(directory);
        }

        @Override
        public File getSourceFile() {
            return findSourceFile(directory);
        }

        @Override
        public File getMappingFile(String metadataPrefix) {
            return findMappingFile(directory, metadataPrefix);
        }

        @Override
        public List<String> getMappingPrefixes() {
            List<String> prefixes = new ArrayList<String>();
            for (File mappingFile : findMappingFiles(directory)) {
                String name = Hasher.extractFileName(mappingFile);
                name = name.substring(FileStore.MAPPING_FILE_PREFIX.length());
                name = name.substring(0, name.length() - FileStore.MAPPING_FILE_SUFFIX.length());
                prefixes.add(name);
            }
            return prefixes;
        }

        @Override
        public void acceptSipZip(ZipInputStream zipInputStream, ProgressListener progressListener) throws FileStoreException {
            ZipEntry zipEntry;
            byte[] buffer = new byte[BLOCK_SIZE];
            long totalBytesRead = 0;
            int bytesRead;
            boolean cancelled = false;
            try {
                while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                    String fileName = zipEntry.getName();
                    File file = new File(directory, fileName);
                    if (fileName.equals(FileStore.SOURCE_FILE_NAME)) {
                        Hasher hasher = new Hasher();
                        GZIPInputStream gzipInputStream = new GZIPInputStream(zipInputStream);
                        GZIPOutputStream outputStream = new GZIPOutputStream(new FileOutputStream(file));
                        while (!cancelled && -1 != (bytesRead = gzipInputStream.read(buffer))) {
                            outputStream.write(buffer, 0, bytesRead);
                            totalBytesRead += bytesRead;
                            if (progressListener != null) {
                                if (!progressListener.setProgress((int) (totalBytesRead / BLOCK_SIZE))) {
                                    cancelled = true;
                                    break;
                                }
                            }
                            hasher.update(buffer, bytesRead);
                        }
                        if (progressListener != null) progressListener.finished(!cancelled);
                        outputStream.close();
                        String hash = hasher.toString();
                        File hashedSource = new File(directory, hash + "__" + SOURCE_FILE_NAME);
                        if (!file.renameTo(hashedSource)) {
                            throw new FileStoreException(String.format("Unable to rename %s to %s", file.getAbsolutePath(), hashedSource.getAbsolutePath()));
                        }
                    }
                    else {
                        IOUtils.copy(zipInputStream, new FileOutputStream(file));
                        Hasher.ensureFileHashed(file);
                    }
                }
            }
            catch (IOException e) {
                throw new FileStoreException("Unable to accept SipZip file", e);
            }
        }

        @Override
        public String toString() {
            return getSpec();
        }

        private File mappingFile(String prefix) {
            return new File(directory, String.format(MAPPING_FILE_PATTERN, prefix));
        }

        private void delete(File file) throws FileStoreException {
            if (file.isDirectory()) {
                for (File sub : file.listFiles()) {
                    delete(sub);
                }
            }
            if (!file.delete()) {
                throw new FileStoreException(String.format("Unable to delete %s", file.getAbsolutePath()));
            }
        }
    }

    private static class MappingOutputImpl implements MappingOutput {
        private RecordMapping recordMapping;
        private File discardedFile, normalizedFile;
        private Writer outputWriter, discardedWriter;
        private int recordsNormalized, recordsDiscarded;

        private MappingOutputImpl(String spec, RecordMapping recordMapping, File normalizedDirectory) throws FileStoreException {
            this.recordMapping = recordMapping;
            try {
                if (normalizedDirectory != null) {
                    this.normalizedFile = new File(normalizedDirectory, String.format("%s_%s_normalized.xml", spec, recordMapping.getPrefix()));
                    this.discardedFile = new File(normalizedDirectory, String.format("%s_%s_discarded.txt", spec, recordMapping.getPrefix()));
                    this.outputWriter = new OutputStreamWriter(new FileOutputStream(normalizedFile), "UTF-8");
                    this.discardedWriter = new OutputStreamWriter(new FileOutputStream(discardedFile), "UTF-8");
                }
            }
            catch (FileNotFoundException e) {
                throw new FileStoreException("Unable to create output files", e);
            }
            catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Writer getOutputWriter() {
            if (outputWriter == null) {
                throw new RuntimeException("Normalized file was not to be stored");
            }
            return outputWriter;
        }

        @Override
        public Writer getDiscardedWriter() {
            if (discardedWriter == null) {
                throw new RuntimeException("Discarded file was not to be stored");
            }
            return discardedWriter;
        }

        @Override
        public void recordNormalized() {
            recordsNormalized++;
        }

        @Override
        public void recordDiscarded() {
            recordsDiscarded++;
        }

        @Override
        public void close(boolean abort) throws FileStoreException {
            try {
                if (abort) {
                    recordMapping.setRecordsNormalized(0);
                    recordMapping.setRecordsDiscarded(0);
                    recordMapping.setNormalizeTime(0);
                    if (outputWriter != null) {
                        outputWriter.close();
                        discardedWriter.close();
                    }
                    if (normalizedFile != null) {
                        normalizedFile.delete();
                    }
                }
                else {
                    if (outputWriter != null) {
                        outputWriter.close();
                        discardedWriter.close();
                    }
                    recordMapping.setRecordsNormalized(recordsNormalized);
                    recordMapping.setRecordsDiscarded(recordsDiscarded);
                    recordMapping.setNormalizeTime(System.currentTimeMillis());
                }
            }
            catch (IOException e) {
                throw new FileStoreException("Unable to close output", e);
            }
        }
    }

    private XStream getAppConfigStream() {
        XStream stream = new XStream();
        stream.processAnnotations(AppConfig.class);
        return stream;
    }

    private File findFactsFile(File dir) {
        File[] files = dir.listFiles(new FactsFileFilter());
        switch (files.length) {
            case 0:
                return new File(dir, FACTS_FILE_NAME);
            case 1:
                return files[0];
            default:
                for (File file : files) {
                    if (Hasher.extractHash(file) == null) {
                        return file;
                    }
                }
                return getMostRecent(files);
        }
    }

    private File findSourceFile(File dir) {
        File[] files = dir.listFiles(new SourceFileFilter());
        switch (files.length) {
            case 0:
                return new File(dir, SOURCE_FILE_NAME);
            case 1:
                return files[0];
            default:
                for (File file : files) {
                    if (Hasher.extractHash(file) == null) {
                        return file;
                    }
                }
                return getMostRecent(files);
        }
    }

    private Collection<File> findMappingFiles(File dir) {
        File[] files = dir.listFiles(new MappingFileFilter());
        Map<String, List<File>> map = new TreeMap<String, List<File>>();
        for (File file : files) {
            String prefix = getMetadataPrefix(file);
            if (prefix == null) continue;
            List<File> list = map.get(prefix);
            if (list == null) {
                map.put(prefix, list = new ArrayList<File>());
            }
            list.add(file);
        }
        List<File> mappingFiles = new ArrayList<File>();
        for (Map.Entry<String, List<File>> entry : map.entrySet()) {
            if (entry.getValue().size() == 1) {
                mappingFiles.add(entry.getValue().get(0));
            }
            else {
                mappingFiles.add(getMostRecent(entry.getValue().toArray(new File[entry.getValue().size()])));
            }
        }
        return mappingFiles;
    }

    private File findMappingFile(File dir, String metadataPrefix) {
        File mappingFile = null;
        for (File file : findMappingFiles(dir)) {
            String prefix = getMetadataPrefix(file);
            if (prefix.equals(metadataPrefix)) {
                mappingFile = file;
            }
        }
        if (mappingFile == null) {
            mappingFile = new File(dir, String.format(MAPPING_FILE_PATTERN, metadataPrefix));
        }
        return mappingFile;
    }

    private class FactsFileFilter implements FileFilter {
        @Override
        public boolean accept(File file) {
            return file.isFile() && FACTS_FILE_NAME.equals(Hasher.extractFileName(file));
        }
    }

    private class SourceFileFilter implements FileFilter {
        @Override
        public boolean accept(File file) {
            return file.isFile() && SOURCE_FILE_NAME.equals(Hasher.extractFileName(file));
        }
    }

    private class MappingFileFilter implements FileFilter {
        @Override
        public boolean accept(File file) {
            String name = Hasher.extractFileName(file);
            return file.isFile() && name.startsWith(MAPPING_FILE_PREFIX) && name.endsWith(MAPPING_FILE_SUFFIX);
        }
    }

    private String getMetadataPrefix(File file) {
        String name = Hasher.extractFileName(file);
        if (name.startsWith(MAPPING_FILE_PREFIX) && name.endsWith(MAPPING_FILE_SUFFIX)) {
            name = name.substring(MAPPING_FILE_PREFIX.length());
            name = name.substring(0, name.length() - MAPPING_FILE_SUFFIX.length());
            return name;
        }
        else {
            return null;
        }
    }

    private File getMostRecent(File[] files) {
        if (files.length > 0) {
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    long lastA = a.lastModified();
                    long lastB = b.lastModified();
                    if (lastA > lastB) {
                        return -1;
                    }
                    else if (lastA < lastB) {
                        return 1;
                    }
                    else {
                        return 0;
                    }
                }
            });
            if (files.length > MAX_HASH_HISTORY) {
                for (int walk = MAX_HASH_HISTORY; walk < files.length; walk++) {
                    //noinspection ResultOfMethodCallIgnored
                    files[walk].delete();
                }
            }
            return files[0];
        }
        else {
            return null;
        }
    }

    private void writeCode(File file, String code) throws IOException {
        FileWriter out = new FileWriter(file);
        out.write(code);
        out.close();
    }

    private String readFileCode(File file) throws IOException {
        FileReader in = new FileReader(file);
        return readCode(in);
    }

    private String readResourceCode(String fileName) throws IOException {
        URL resource = getClass().getResource("/" + fileName);
        InputStream in = resource.openStream();
        Reader reader = new InputStreamReader(in);
        return readCode(reader);
    }

    private String readCode(Reader reader) throws IOException {
        BufferedReader in = new BufferedReader(reader);
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }
            out.append(line).append('\n');
        }
        in.close();
        return out.toString();
    }
}
