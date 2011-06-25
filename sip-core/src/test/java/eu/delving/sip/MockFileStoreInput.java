package eu.delving.sip;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * Provide some sample data for tests
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class MockFileStoreInput {
    private static final String SAMPLE_INPUT = "/mock-file-store-input.xml.gz";

    public static File sampleFile() throws IOException {
        return new File(MockFileStoreInput.class.getResource(SAMPLE_INPUT).getFile());
    }

    public static InputStream sampleInputStream() throws IOException {
        return new GZIPInputStream(MockFileStoreInput.class.getResource(SAMPLE_INPUT).openStream());
    }

}
