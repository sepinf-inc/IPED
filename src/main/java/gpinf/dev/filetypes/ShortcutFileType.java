package gpinf.dev.filetypes;

//import gpinf.util.icons.IconUtil;

/**
 * Implementação da classe utilizada para arquivos de atalho (LNK).
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class ShortcutFileType extends AlternateHtmlFileType {
	/** Identificador de serialização. */
	private static final long serialVersionUID = -1028509998869579831L;

	/**
	 * Ícone associado ao tipo. * private static final Icon icon =
	 * IconUtil.createIcon("ft-shortcut");
	 * 
	 * /** Retorna a descrição longa padrão.
	 */
	@Override
	public String getLongDescr() {
		return "Arquivo de Atalho";
	}

	/**
	 * Retorna o ícone correspondente ao tipo de arquivo. * public Icon
	 * getIcon() { return icon; }
	 */
}
