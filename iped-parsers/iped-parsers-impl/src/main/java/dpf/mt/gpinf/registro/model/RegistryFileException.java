package dpf.mt.gpinf.registro.model;

public class RegistryFileException extends Exception {

    public RegistryFileException(Exception e) {
        super(e);
    }

    public RegistryFileException(String mensagem) {
        super(mensagem);
    }
}
