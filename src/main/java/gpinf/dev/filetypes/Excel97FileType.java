package gpinf.dev.filetypes;

//import gpinf.util.icons.IconUtil;

/**
 * Subclasse utilizada para arquivos do Microsoft Excel 97.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class Excel97FileType extends ExcelFileType {
	/** Identificador de serialização. */
	private static final long serialVersionUID = -402209264689555990L;

	/** Ícone associado ao tipo. */
	// private static final Icon icon = IconUtil.createIcon("ft-excel");

	/**
	 * @return Descrição longa.
	 */
	@Override
	public String getLongDescr() {
		return "Documento do MS Excel 97";
	}

	/**
	 * Retorna o ícone correspondente ao tipo de arquivo. * public Icon
	 * getIcon() { return icon; }
	 */
}
