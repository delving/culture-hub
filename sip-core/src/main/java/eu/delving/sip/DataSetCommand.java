package eu.delving.sip;

/**
 * An enumeration of the commands that the client can send to the server about a data set
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public enum DataSetCommand {
    INDEX,
    REINDEX,
    DISABLE,
    DELETE
}
