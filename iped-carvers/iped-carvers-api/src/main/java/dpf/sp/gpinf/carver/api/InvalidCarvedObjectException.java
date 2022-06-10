package dpf.sp.gpinf.carver.api;

public class InvalidCarvedObjectException extends Exception {

    private static final long serialVersionUID = 1L;

	public InvalidCarvedObjectException(String message) {
        super(message);
    }

    public InvalidCarvedObjectException(Exception e) {
        super(e.getMessage(), e);
    }

}
