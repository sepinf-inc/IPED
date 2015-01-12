package gpinf.dev.filetypes;

/**
 * Subclasse utilizada para arquivos do Microsoft Word 97.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class Word97DocumentFileType extends WordDocumentFileType {
	/** Identificador de serialização. */
	private static final long serialVersionUID = -402209264689555990L;

	/**
	 * @return Descrição longa.
	 */
	@Override
	public String getLongDescr() {
		return "Documento do MS Word 97";
	}
}
