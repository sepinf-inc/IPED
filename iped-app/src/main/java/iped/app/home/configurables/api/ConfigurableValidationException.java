package iped.app.home.configurables.api;

public class ConfigurableValidationException extends Exception {

    public ConfigurableValidationException(String msg, Exception cause) {
        super(msg, cause);
    }

    public ConfigurableValidationException(Exception cause) {
        super(cause);
    }

}
