package gpinf.dev.filetypes;

/**
 * Subclasse utilizada para arquivos do Microsoft Word 2000.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class Word2000DocumentFileType extends WordDocumentFileType {
	/** Identificador de serialização. */
	private static final long serialVersionUID = -1872695488196699176L;

	/**
	 * @return Descrição longa.
	 */
	@Override
	public String getLongDescr() {
		return "Documento do MS Word 2000";
	}
}
