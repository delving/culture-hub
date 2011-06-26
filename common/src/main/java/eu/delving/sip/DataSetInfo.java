package eu.delving.sip;

import com.thoughtworks.xstream.annotations.XStreamAlias;

import java.util.List;

/**
 *
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

@XStreamAlias("dataset")
public class DataSetInfo {
    public String spec;
    public String name;
    public String state;
    public Integer recordsIndexed;
    public Integer recordCount;
    public String errorMessage;
    public List<String> hashes;

    public boolean hasHash(String hash) {
        if (hashes != null) {
            for (String ours : hashes) {
                if (ours.equals(hash)) {
                    return true;
                }
            }
        }
        return false;
    }
}
