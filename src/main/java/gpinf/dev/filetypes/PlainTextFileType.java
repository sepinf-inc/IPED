package gpinf.dev.filetypes;

import gpinf.dev.data.EvidenceFile;

import java.io.File;
import java.util.List;

/**
 * Implementação da classe utilizada para arquivos texto.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class PlainTextFileType extends EvidenceFileType {
	/** Identificador utilizado para serialização. */
	private static final long serialVersionUID = 543421345792837792L;

	/**
	 * Ícone associado ao tipo. * private static final Icon icon =
	 * IconUtil.createIcon("ft-plain-text");
	 * 
	 * /** Retorna o tipo de visulização que deve ser utilizado pelo
	 * visualizador para este tipo de arquivo.
	 * 
	 * @return Tipo de visualização "Texto".
	 * 
	 *         public ViewType getViewType() { return ViewType.TEXT; }
	 * 
	 *         /**
	 * @return String fixa com a descrição na forma longa.
	 */
	@Override
	public String getLongDescr() {
		return "Documento Texto";
	}

	/**
	 * Processa arquivos deste tipo.
	 * 
	 * @param baseDir
	 *            diretório base onde arquivo de evidência exportados estão
	 *            armazenados
	 * @param evidenceFiles
	 *            lista de arquivos a ser processada
	 */
	@Override
	public void processFiles(File baseDir, List<EvidenceFile> evidenceFiles) {
		// Nada a ser feito por enquanto
	}

	/**
	 * Retorna o ícone correspondente ao tipo de arquivo. * public Icon
	 * getIcon() { return icon; }
	 */
}
