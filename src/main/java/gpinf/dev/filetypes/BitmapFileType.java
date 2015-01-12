package gpinf.dev.filetypes;

/**
 * Implementação da classe utilizada para arquivos de Imagem BMP.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class BitmapFileType extends ImageFileType {
	/** Identificador utilizado para serialização. */
	private static final long serialVersionUID = -9067597753342517299L;

	/**
	 * @return Descrição longa.
	 */
	@Override
	public String getLongDescr() {
		return "Imagem Bitmap";
	}
}
