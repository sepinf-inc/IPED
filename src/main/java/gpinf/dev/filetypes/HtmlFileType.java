package gpinf.dev.filetypes;

import gpinf.dev.DEVConstants;
import gpinf.dev.data.EvidenceFile;
import gpinf.util.ProcessUtil;

import java.io.File;
import java.util.List;

/**
 * Implementação da classe utilizada para no formato HTML.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class HtmlFileType extends EvidenceFileType {
	/** Identificador de serialização. */
	private static final long serialVersionUID = 1076685075658011169L;

	/** Ícone associado ao tipo. */
	// private static final Icon icon = IconUtil.createIcon("ft-html");

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
			String exp = evidenceFile.getExportedFile();
			if (exp == null)
				continue;
			String view = getViewFile(exp, "pdf");
			evidenceFile.setViewFile(view);
			File fview = new File(baseDir, view);
			if (fview.exists())
				continue;

			if (sb.length() > 0)
				sb.append(lineSep);
			sb.append(i).append(';');
			sb.append(new File(baseDir, exp).getAbsolutePath()).append(';');
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
	 * 
	 *         /** Retorna a descrição longa.
	 */
	@Override
	public String getLongDescr() {
		return "Página HTML";
	}

	/**
	 * Retorna o ícone correspondente ao tipo de arquivo. * public Icon
	 * getIcon() { return icon; }
	 */
}
