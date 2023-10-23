package iped.parsers.browsers.edge;

public class EdgeWebCacheException extends Exception {
    private static final long serialVersionUID = 1L;

	public EdgeWebCacheException() {

    }

    public EdgeWebCacheException(String exception) {
        super(exception);
    }

    public EdgeWebCacheException(Exception source) {
        super(source);
    }
}
