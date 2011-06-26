package eu.delving.metadata;

import com.thoughtworks.xstream.XStream;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The extra data required when uploading a zip to the repository
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class Facts {
    private static final String DOWNLOADED_SOURCE = "downloadedSource";
    private static final String RECORD_ROOT_PATH = "recordRootPath";
    private static final String UNIQUE_ELEMENT_PATH = "uniqueElementPath";
    private static final String RECORD_COUNT = "recordCount";

    private Map<String, String> map = new TreeMap<String, String>();

    public boolean set(String name, String value) {
        if (!FIELD_SET.contains(name)) {
            throw new IllegalArgumentException(String.format("[%s] is not a fact name", name));
        }
        String existing = map.get(name);
        if (existing == null || !value.equals(existing)) {
            map.put(name, value);
            return true;
        }
        return false;
    }

    public String get(String name) {
        if (!FIELD_SET.contains(name)) {
            throw new IllegalArgumentException(String.format("[%s] is not a fact name", name));
        }
        String value = map.get(name);
        if (value == null) {
            map.put(name, value = "");
        }
        return value;
    }

    public boolean isDownloadedSource() {
        return get(DOWNLOADED_SOURCE).equalsIgnoreCase("true");
    }

    public void setDownloadedSource(boolean downloadedSource) {
        set(DOWNLOADED_SOURCE, String.valueOf(downloadedSource));
    }

    public String getRecordRootPath() {
        if (isDownloadedSource()) {
            return String.format("/%s/%s", SourceStream.ENVELOPE_TAG, SourceStream.RECORD_TAG);
        }
        else {
            return get(RECORD_ROOT_PATH);
        }
    }

    public void setRecordRootPath(String value) {
        setDownloadedSource(false);
        set(RECORD_ROOT_PATH, value);
    }

    public String getRecordCount() {
        return get(RECORD_COUNT);
    }

    public void setRecordCount(String value) {
        set(RECORD_COUNT, value);
    }

    public String getUniqueElementPath() {
        if (isDownloadedSource()) {
            return getRecordRootPath() + getRelativeUniquePath();
        }
        else {
            return get(UNIQUE_ELEMENT_PATH);
        }
    }

    public void setUniqueElementPath(String value) {
        setDownloadedSource(false);
        set(UNIQUE_ELEMENT_PATH, value);
    }

    public boolean isValid() {
        for (String field : FIELD_SET) {
            if (get(field).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static FactDefinition.List listDefinition;
    private static Set<String> FIELD_SET = new TreeSet<String>();

    static {
        try {
            if (listDefinition == null) {
                XStream stream = new XStream();
                stream.processAnnotations(FactDefinition.List.class);
                Reader reader = new InputStreamReader(Facts.class.getResource("/fact-definition-list.xml").openStream(), "UTF-8");
                listDefinition = (FactDefinition.List) stream.fromXML(reader);
                for (FactDefinition factDefinition : listDefinition.factDefinitions) {
                    FIELD_SET.add(factDefinition.name);
                }
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to read fact-definition-list.xml from resources");
        }
    }

    public static List<FactDefinition> definitions() {
        return listDefinition.factDefinitions;
    }

    public static Facts fromBytes(byte[] array) throws MetadataException {
        ByteArrayInputStream bais = new ByteArrayInputStream(array);
        return read(bais);
    }

    public static byte[] toBytes(Facts facts) throws MetadataException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        write(facts, baos);
        return baos.toByteArray();
    }

    public static Facts read(InputStream inputStream) throws MetadataException {
        try {
            Facts facts = new Facts();
            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("#")) continue;
                int equals = line.indexOf("=");
                if (equals < 0) {
                    continue;
                }
                String fieldName = line.substring(0, equals).trim();
                String value = line.substring(equals + 1).trim();
                if (FIELD_SET.contains(fieldName)) {
                    facts.set(fieldName, value);
                }
            }
            in.close();
            return facts;
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        catch (IOException e) {
            throw new MetadataException("Unable to read facts", e);
        }
    }

    public static void write(Facts facts, OutputStream outputStream) throws MetadataException {
        try {
            Writer writer = new OutputStreamWriter(outputStream, "UTF-8");
            for (FactDefinition factDefinition : listDefinition.factDefinitions) {
                writer.write(String.format("%s=%s\n", factDefinition.name, facts.get(factDefinition.name)));
            }
            writer.close();
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        catch (IOException e) {
            throw new MetadataException("Unable to write facts", e);
        }
    }

    public String getRelativeUniquePath() {
        String recordRootPath = get(RECORD_ROOT_PATH);
        String uniqueElementPath = get(UNIQUE_ELEMENT_PATH);
        if (recordRootPath.equals(uniqueElementPath.substring(0, recordRootPath.length()))) {
            return uniqueElementPath.substring(recordRootPath.length());
        }
        else {
            return uniqueElementPath; // this is a very improper answer
        }
    }
}
