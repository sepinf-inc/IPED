package iped.exception;

public class IPEDException extends RuntimeException {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

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
