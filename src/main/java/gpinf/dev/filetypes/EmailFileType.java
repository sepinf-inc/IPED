package gpinf.dev.filetypes;

//import gpinf.util.icons.IconUtil;

/**
 * Implementação da classe utilizada para mensagens de email.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class EmailFileType extends AlternateHtmlFileType {
	/** Identificador de serialização. */
	private static final long serialVersionUID = -734327432747271409L;

	/** Ícone associado ao tipo. */
	// private static final Icon icon = IconUtil.createIcon("ft-email");

	/**
	 * Retorna a descrição longa.
	 */
	@Override
	public String getLongDescr() {
		return "Mensagem de E-mail";
	}

	/**
	 * Retorna o ícone correspondente ao tipo de arquivo. * public Icon
	 * getIcon() { return icon; }
	 */
}
