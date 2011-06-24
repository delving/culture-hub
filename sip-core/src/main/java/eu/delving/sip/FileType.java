package eu.delving.sip;

/**
 * the types of files that are uploaded
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public enum FileType {

    FACTS(
            "text/plain"
    ),
    SOURCE(
            "application/x-gzip"
    ),
    MAPPING(
            "text/xml"
    );

    private String contentType;

    FileType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }
}
