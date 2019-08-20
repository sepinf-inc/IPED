package gpinf.dev.data;

import iped3.IProperty;

/**
 * Classe que define uma propriedade, composta pelo par (nome para valor).
 *
 * @author Wladimir Leite (GPINF/SP)
 */
public class Property implements IProperty {

    /**
     * Identificador utilizado para serialização da classe
     */
    private static final long serialVersionUID = 80014119327L;

    /**
     * Nome da propriedade.
     */
    private final String name;

    /**
     * Valor da propriedade.
     */
    private final String value;

    /**
     * Cria nova propriedade.
     *
     * @param name
     *            nome da propriedade
     * @param value
     *            valor correspondente
     */
    public Property(final String name, final String value) {
        this.name = name;
        this.value = value;
    }

    /**
     * Retorna representação em texto da propriedade.
     */
    @Override
    public String toString() {
        return name + ": " + value; //$NON-NLS-1$
    }

    /**
     * Obtém nome da propriedade.
     *
     * @return nome da propriedade
     */
    public String getName() {
        return name;
    }

    /**
     * Obtém valor da propriedade.
     *
     * @return valor correspondente da propriedade
     */
    public String getValue() {
        return value;
    }
}
