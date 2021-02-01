package gpinf.util;

import java.text.DateFormat;
import java.util.Date;

/**
 * Exceção que indica que o formato de data encontrado não é o esperado.
 *
 * @author Wladimir Leite (GPINF/SP)
 */
public class DateFormatException extends Exception {

    /**
     * Constante de serialização da classe.
     */
    private static final long serialVersionUID = 3781843273283L;

    /**
     * Cria uma nova exceção acompanhada de uma mensagem.
     *
     * @param msg
     *            mensagem explicativa da causa da exceção
     */
    public DateFormatException(String msg) {
        super(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(new Date()) + "\t[WARN]\t" //$NON-NLS-1$
                + msg);
    }
}
