package dpf.sp.gpinf.indexer.util;

public class IPEDException extends RuntimeException {

    public IPEDException(String msg) {
        super(msg);
    }

    public IPEDException(String msg, Throwable e) {
        super(msg, e);
    }

    public IPEDException(Throwable e) {
        super(e);
    }

}
