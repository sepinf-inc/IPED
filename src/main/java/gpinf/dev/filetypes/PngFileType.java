package gpinf.dev.filetypes;

/**
 * Implementação da classe utilizada para arquivos de Imagem PNG.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class PngFileType extends ImageFileType {
	/** Identificador utilizado para serialização. */
	private static final long serialVersionUID = 4756107703652429986L;

	/**
	 * @return Descrição longa.
	 */
	@Override
	public String getLongDescr() {
		return "Imagem PNG (Portable Network Graphics)";
	}

	/**
	 * @return String fixa com a descrição na forma curta.
	 */
	@Override
	public String getShortDescr() {
		return "Imagem PNG";
	}
}
