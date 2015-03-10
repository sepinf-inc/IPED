package gpinf.dev.filetypes;

import gpinf.dev.data.EvidenceFile;
import gpinf.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Implementação da classe utilizada para arquivos não suportados
 * (desconhecidos).
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class GenericFileType extends EvidenceFileType {
	/** Identificador para serialização. */
	private static final long serialVersionUID = 17897987897L;

	/**
	 * Ícone associado ao tipo. * private static final Icon icon =
	 * IconUtil.createIcon("ft-unknown");
	 * 
	 * /** Descrição do tipo de arquivo.
	 */
	private String descr;

	/** Construtor que recebe a descrição do tipo de arquivo.
	 * @param descr
	 *            Descrição a ser utilizada pélo método de obtenção da
	 *            descrição.
	 */
	public GenericFileType(String descr) {
		this.descr = descr;
	}

	/**
	 * Retorna a descrição fornecida no construtor.
	 * 
	 * @return descrição do tipo de arquivo
	 */
	@Override
	public String getLongDescr() {
		return descr;
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
		for (int i = 0; i < evidenceFiles.size(); i++) {
			EvidenceFile evidenceFile = evidenceFiles.get(i);

			// Se houver uma visualização alternativa em PDF utilizar
			String alt = evidenceFile.getAlternativeFile();
			if (alt != null && alt.toLowerCase().endsWith(".pdf")) {
				GenericFileType ft = (GenericFileType) evidenceFile.getType();
				evidenceFile.setViewFile(alt);
				// ft.setViewType(ViewType.PDF);
				continue;
			}

			// Se arquivo for texto pequeno utilizar visualização texto direta
			File exp = new File(baseDir, evidenceFile.getExportedFile());
			try {
				if (exp.length() > 10 << 20)
					continue; // No máximo 10 Mbytes
				int max = (int) (exp.length() / 100); // No máximo 1% de
														// caracteres
														// "não texto"
				String str = FileUtil.read(exp);
				boolean isPlainText = true;
				int cnt = 0;
				for (int j = 0; j < str.length(); j++) {
					char c = str.charAt(j);
					if ((c < 32 && !Character.isWhitespace(c)) || !Character.isDefined(c)) {
						if (++cnt > max) {
							isPlainText = false;
							break;
						}
					}
				}
				if (isPlainText) {
					GenericFileType ft = (GenericFileType) evidenceFile.getType();
					// ft.setViewType(ViewType.TEXT);
				}
			} catch (IOException e) {
				e.printStackTrace();
				continue;
			}
		}
	}

	/**
	 * Retorna o ícone correspondente ao tipo de arquivo. * public Icon
	 * getIcon() { return icon; }
	 */
}
