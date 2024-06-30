package iped.app.home.processmanager;/*
                                     * @created 21/12/2022
                                     * @project IPED
                                     * @author Thiago S. Figueiredo
                                     */

public class IpedStartException extends Exception {

    public IpedStartException(String message) {
        super(message);
    }

    public IpedStartException(String message, Throwable cause) {
        super(message, cause);
    }
}
