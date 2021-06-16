package org.apache.tika.parser.mp4;

import org.apache.tika.metadata.Metadata;

public class ISO6709Converter extends ISO6709Extractor {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public void populateLocation(Metadata metadata, String location) {
        super.extract(location, metadata);
    }

}
