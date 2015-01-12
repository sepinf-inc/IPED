package gpinf.dev.filetypes;

import gpinf.dev.DEVConstants;
import gpinf.dev.data.EvidenceFile;
import gpinf.util.ProcessUtil;

import java.io.File;
import java.util.List;

/**
 * Implementação da classe base utilizada para tipos de arquivo que possuem
 * visualização alternativa no formato HTML.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public abstract class AlternateHtmlFileType extends EvidenceFileType {
	/** Identificador de serialização. */
	private static final long serialVersionUID = 274727140925552473L;

	/**
	 * Processa arquivos deste tipo. Passa uma lista de arquivos para um
	 * programa externo que converte os arquivos para o formato PDF.
	 * 
	 * @param baseDir
	 *            diretório base onde arquivo de evidência exportados estão
	 *            armazenados
	 * @param evidenceFiles
	 *            lista de arquivos a ser processada
	 */
	@Override
	public void processFiles(File baseDir, List<EvidenceFile> evidenceFiles) {
		StringBuilder sb = new StringBuilder();
		String lineSep = System.getProperty("line.separator");
		sb.append(">>NoProps<<").append(lineSep);
		sb.append(">>NoMargins<<").append(lineSep);
		boolean isDone = true;
		for (int i = 0; i < evidenceFiles.size(); i++) {
			EvidenceFile evidenceFile = evidenceFiles.get(i);
			String alt = evidenceFile.getAlternativeFile();
			if (alt == null)
				continue;
			String view = getViewFile(alt, "pdf");
			evidenceFile.setViewFile(view);
			File fview = new File(baseDir, view);
			if (fview.exists())
				continue;

			sb.append(lineSep).append(i).append(';');
			sb.append(new File(baseDir, alt).getAbsolutePath()).append(';');
			fview.getParentFile().mkdirs();
			sb.append(fview.getAbsolutePath());
			isDone = false;
		}
		if (isDone)
			return;

		String result = ProcessUtil.run(DEVConstants.DOC2PDF, sb.toString());
		if (result == null)
			return;

		// Trata resultado
		String[] lines = result.split("\n");
		EvidenceFile evidenceFile = null;
		for (String line : lines) {
			if (line.length() == 0)
				continue;
			if (line.charAt(0) != ' ') {
				int idx = Integer.parseInt(line.split(";")[0]);
				evidenceFile = evidenceFiles.get(idx);
				if (!(new File(baseDir, evidenceFile.getViewFile()).exists()))
					evidenceFile.setViewFile(null);
				continue;
			}
		}
	}

	/**
	 * Retorna o tipo de visulização que deve ser utilizado pelo visualizador
	 * para este tipo de arquivo.
	 * 
	 * @return Tipo de visualização "Pdf".
	 * 
	 *         public ViewType getViewType() { return ViewType.PDF; }
	 */
}
