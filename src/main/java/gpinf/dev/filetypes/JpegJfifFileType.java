package gpinf.dev.filetypes;

/**
 * Implementação da classe utilizada para arquivos de Imagem JPEG subtipo JFIF.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class JpegJfifFileType extends JpegFileType {
	/** Identificador utilizado para serialização. */
	private static final long serialVersionUID = 927471538126310L;

	/**
	 * @return descrição longa.
	 */
	@Override
	public String getLongDescr() {
		return "Imagem JPEG (JFIF)";
	}
}
