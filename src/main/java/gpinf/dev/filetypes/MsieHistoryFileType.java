package gpinf.dev.filetypes;

//import gpinf.util.icons.IconUtil;

/**
 * Implementação da classe utilizada para arquivos de Histórico do Internet
 * Explorer (index.dat).
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class MsieHistoryFileType extends AlternateHtmlFileType {
	/** Identificador de serialização. */
	private static final long serialVersionUID = 737726365439447123L;

	/**
	 * Ícone associado ao tipo. * private static final Icon icon =
	 * IconUtil.createIcon("ft-history");
	 * 
	 * /** Retorna a descrição longa.
	 */
	@Override
	public String getLongDescr() {
		return "Histórico de Internet (Internet Explorer)";
	}

	/**
	 * Retorna a descrição curta.
	 */
	@Override
	public String getShortDescr() {
		return "Histórico de Internet";
	}

	/**
	 * Retorna o ícone correspondente ao tipo de arquivo. * public Icon
	 * getIcon() { return icon; }
	 */
}
