package gpinf.dev.filetypes;

/**
 * Implementação da classe utilizada para arquivos de Imagem JPEG.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public abstract class JpegFileType extends ImageFileType {
	/** Identificador utilizado para serialização. */
	private static final long serialVersionUID = 4124639079253778543L;

	/**
	 * @return String fixa com a descrição na forma curta.
	 */
	@Override
	public String getShortDescr() {
		return "Imagem JPEG";
	}
}
