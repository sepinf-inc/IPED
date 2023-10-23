package iped.parsers.registry.model;

public class RegistryFileException extends Exception {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public RegistryFileException(Exception e) {
        super(e);
    }

    public RegistryFileException(String mensagem) {
        super(mensagem);
    }
}
