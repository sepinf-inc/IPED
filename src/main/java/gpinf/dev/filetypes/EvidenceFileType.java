package gpinf.dev.filetypes;

import gpinf.dev.DEVConstants;
import gpinf.dev.data.EvidenceFile;

import java.io.File;
import java.io.Serializable;
import java.util.List;

/**
 * Classe abstrata que define métodos obrigatórios para implementações de tipos
 * de arquivo.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public abstract class EvidenceFileType implements Serializable {
	/** Identificador utilizado para serialização. */
	private static final long serialVersionUID = 2631182556165657250L;

	/**
	 * Retorna a descrição do tipo de arquivo de evidência. Por exemplo:
	 * "Documento do MS Word 2000"
	 * 
	 * @return String com a descrição na forma longa do tipo de arquivo.
	 */
	public abstract String getLongDescr();

	/**
	 * Processa lista de arquivos de evidência. Este método é responsável por
	 * "processar" todos arquivos de evidência de um determinado tipo e deve ser
	 * sobrescrito pela implementações de classes de tipo de arquivo.
	 * "Processar" significa extrair informações adicionais e converter para um
	 * formato tratado pelo visualizador de arquivo. Por questões de performance
	 * este método recebe uma lista de arquivos e não atua sobre um arquivo e
	 * deveria ser declarado como "static". Porém a linguagem Java não permite
	 * que métodos estáticos sejam sobrescritos, por isso é declarado como um
	 * método comum, ou seja, é feito um sacrifício nas boas práticas de
	 * utilização da linguagem em benefício da performance.
	 * 
	 * @param baseDir
	 *            diretório base onde arquivo de evidência exportados estão
	 *            armazenados
	 * @param evidenceFiles
	 *            lista de arquivos a ser processada
	 */
	public abstract void processFiles(File baseDir, List<EvidenceFile> evidenceFiles);

	/**
	 * Retorna a descrição do tipo de arquivo de evidência. Por exemplo:
	 * "Documento do Word".
	 * 
	 * @return String com a descrição na forma curta do tipo de arquivo.
	 */
	public String getShortDescr() {
		// Implementação padrão retorna a descrição longa
		return getLongDescr();
	}

	/**
	 * Retorna o tipo de visulização que deve ser utilizado pelo visualizador
	 * para este tipo de arquivo.
	 * 
	 * @return Tipo de visualização.
	 * 
	 *         public ViewType getViewType() { //Implementação padrão retorna
	 *         "sem visualização" return ViewType.NONE; }
	 * 
	 *         /** Método auxiliar que monta o caminho de um arquivo de
	 *         visualização.
	 * @param fileName
	 *            nome do arquivo original
	 * @param extension
	 *            nova extensão a ser utilizada
	 * @return Novo nome do arquivo, trocando o diretório inicial para VIEW_DIR
	 *         e substituindo a extensão original pela especificada.
	 */
	protected static String getViewFile(String fileName, String extension) {
		int p1 = 0;
		for (int j = 0; j < fileName.length(); j++) {
			if (fileName.charAt(j) == '/' || fileName.charAt(j) == '\\') {
				p1 = j;
				break;
			}
		}
		String view = DEVConstants.VIEW_DIR + ((p1 == fileName.length()) ? ("/" + fileName) : fileName.substring(p1));
		p1 = view.lastIndexOf('.');
		if (p1 >= 0)
			return view.substring(0, p1 + 1) + extension;
		return view + "." + extension;
	}

	/**
	 * Retorna o ícone correspondente ao tipo de arquivo.
	 */
	// public abstract Icon getIcon();
}
