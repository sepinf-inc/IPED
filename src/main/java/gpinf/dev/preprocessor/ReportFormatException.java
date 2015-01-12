package gpinf.dev.preprocessor;

import java.text.DateFormat;
import java.util.Date;

/**
 * Exceção que indica que o formato do arquivo de relatório não é o esperado.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class ReportFormatException extends Exception {
	/** Constante de serialização da classe. */
	private static final long serialVersionUID = 5637528346327128160L;

	/**
	 * Cria uma nova exceção acompanhada de uma mensagem.
	 * 
	 * @param msg
	 *            mensagem explicativa da causa da exceção
	 */
	public ReportFormatException(String msg) {
		super(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(new Date()) + "\t[AVISO]\t" + msg);
	}
}
