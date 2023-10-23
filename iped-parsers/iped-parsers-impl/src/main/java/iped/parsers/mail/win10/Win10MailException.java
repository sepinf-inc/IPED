package iped.parsers.mail.win10;

public class Win10MailException extends Exception {
    private static final long serialVersionUID = 1L;

	public Win10MailException() {
    }

    public Win10MailException(String exception) {
        super(exception);
    }

    public Win10MailException(Exception source) {
        super(source);
    }
}
