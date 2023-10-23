package iped.parsers.whatsapp;

/**
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@pf.gov.br>
 */
public class WAExtractorException extends Exception {
    /**
     * 
     */
    private static final long serialVersionUID = 8304329195364275698L;

    public WAExtractorException() {

    }

    public WAExtractorException(Exception source) {
        super(source);
    }
}
