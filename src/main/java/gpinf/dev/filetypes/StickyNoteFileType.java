package gpinf.dev.filetypes;

//import gpinf.util.icons.IconUtil;

/**
 * Implementação da classe utilizada para arquivos "Sticky Note".
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class StickyNoteFileType extends AlternateHtmlFileType {
	/** Identificador de serialização. */
	private static final long serialVersionUID = 1792517005738710969L;

	/**
	 * Ícone associado ao tipo. * private static final Icon icon =
	 * IconUtil.createIcon("ft-sticky-note");
	 * 
	 * /** Retorna a descrição longa.
	 */
	@Override
	public String getLongDescr() {
		return "Arquivo de Anotação (Sticky Note)";
	}

	/**
	 * Retorna a descrição curta.
	 */
	@Override
	public String getShortDescr() {
		return "Arquivo de Anotação";
	}

	/**
	 * Retorna o ícone correspondente ao tipo de arquivo. * public Icon
	 * getIcon() { return icon; }
	 */
}
