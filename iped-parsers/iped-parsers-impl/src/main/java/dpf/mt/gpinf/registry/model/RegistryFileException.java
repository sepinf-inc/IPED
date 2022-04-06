package dpf.mt.gpinf.registry.model;

public class RegistryFileException extends Exception {

    public RegistryFileException(Exception e) {
        super(e);
    }

    public RegistryFileException(String mensagem) {
        super(mensagem);
    }
}
