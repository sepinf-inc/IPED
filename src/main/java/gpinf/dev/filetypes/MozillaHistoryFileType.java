package gpinf.dev.filetypes;

//import gpinf.util.icons.IconUtil;

/**
 * Implementação da classe utilizada para arquivos de Histórico do Mozilla
 * (history.dat).
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class MozillaHistoryFileType extends AlternateHtmlFileType {
	/** Identificador de serialização. */
	private static final long serialVersionUID = -3673284414195434280L;

	/**
	 * Ícone associado ao tipo. * private static final Icon icon =
	 * IconUtil.createIcon("ft-history");
	 * 
	 * /** Retorna a descrição longa.
	 */
	@Override
	public String getLongDescr() {
		return "Histórico de Internet (Mozilla/Netscape)";
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
