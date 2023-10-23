package iped.engine.task.transcript;

import java.net.ConnectException;

public class TooManyConnectException extends ConnectException {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public TooManyConnectException() {
        super("Too many connection errors to transcription server, maybe it is down.");
    }

}
