package gpinf.dev.filetypes;

/**
 * Subclasse utilizada para arquivos do Microsoft Word XP.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class WordXpDocumentFileType extends WordDocumentFileType {
	/** Identificador de serialização. */
	private static final long serialVersionUID = 6125557878204811971L;

	/**
	 * @return Descrição longa.
	 */
	@Override
	public String getLongDescr() {
		return "Documento do MS Word XP";
	}
}
