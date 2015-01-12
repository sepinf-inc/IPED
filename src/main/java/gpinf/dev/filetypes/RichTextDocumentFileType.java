package gpinf.dev.filetypes;

//import gpinf.util.icons.IconUtil;

/**
 * Subclasse utilizada para arquivos RTF.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class RichTextDocumentFileType extends WordDocumentFileType {
	/** Identificador de serialização. */
	private static final long serialVersionUID = 7911108812824497050L;

	/**
	 * Ícone associado ao tipo. * private static final Icon icon =
	 * IconUtil.createIcon("ft-rtf");
	 * 
	 * /**
	 * 
	 * @return Descrição curta.
	 */
	@Override
	public String getShortDescr() {
		return "Documento RTF";
	}

	/**
	 * @return Descrição longa.
	 */
	@Override
	public String getLongDescr() {
		return "Documento no Formato Rich Text (RTF)";
	}

	/**
	 * Retorna o ícone correspondente ao tipo de arquivo. * public Icon
	 * getIcon() { return icon; }
	 */
}
