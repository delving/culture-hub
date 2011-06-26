package eu.europeana.sip.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Gather some validation problems together into an exception that can be thrown
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class RecordValidationException extends Exception {
    private MetadataRecord metadataRecord;
    private List<String> problems = new ArrayList<String>();

    public RecordValidationException(MetadataRecord metadataRecord, List<String> problems) {
        super(problems.size() + " Record Validation Problems");
        this.metadataRecord = metadataRecord;
        this.problems = problems;
    }

    public MetadataRecord getMetadataRecord() {
        return metadataRecord;
    }

    public List<String> getProblems() {
        return problems;
    }

    public String toString() {
        StringBuilder out = new StringBuilder("Problems:\n");
        for (String problem : problems) {
            out.append(problem).append('\n');
        }
        return out.toString();
    }
}
