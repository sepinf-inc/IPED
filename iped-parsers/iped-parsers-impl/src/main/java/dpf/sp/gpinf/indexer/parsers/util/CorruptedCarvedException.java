package dpf.sp.gpinf.indexer.parsers.util;

import org.apache.tika.exception.TikaException;

public class CorruptedCarvedException extends TikaException {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public CorruptedCarvedException(Exception e) {
        super("The carved file is corrupted", e);
    }

}
