package gpinf.dev.filetypes;

import gpinf.dev.DEVConstants;
import gpinf.dev.data.EvidenceFile;
import gpinf.dev.data.Property;
import gpinf.util.ProcessUtil;

import java.io.File;
import java.util.List;

/**
 * Implementação da classe utilizada para arquivos do Microsoft Excel(XLS).
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public abstract class ExcelFileType extends EvidenceFileType {
	/** Identificador de serialização. */
	private static final long serialVersionUID = 2649882085603257820L;

	/**
	 * Processa arquivos deste tipo. Passa uma lista de arquivos para um
	 * programa externo que converte os arquivos para o formato PDF e retorna
	 * uma lista com os metadados (propriedades) de cada arquivo.
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
		for (int i = 0; i < evidenceFiles.size(); i++) {
			EvidenceFile evidenceFile = evidenceFiles.get(i);
			String exp = evidenceFile.getExportedFile();
			String view = getViewFile(exp, "pdf");
			evidenceFile.setViewFile(view);
			File fview = new File(baseDir, view);

			if (sb.length() > 0)
				sb.append(lineSep);
			sb.append(i).append(';');
			sb.append(new File(baseDir, exp).getAbsolutePath()).append(';');
			fview.getParentFile().mkdirs();

			sb.append((fview.exists()) ? "-" : fview.getAbsolutePath());
			String password = evidenceFile.getPropertyByName("senha");
			if (password != null)
				sb.append(";").append(password);
		}

		String result = ProcessUtil.run(DEVConstants.XLS2PDF, sb.toString());
		if (result == null)
			return;

		// Trata resultado, processando as propriedades
		String[] lines = result.split("\n");
		String category = "Propriedades da Planilha";
		EvidenceFile evidenceFile = null;
		for (String line : lines) {
			if (line.charAt(0) != ' ') {
				int idx = Integer.parseInt(line.split(";")[0]);
				evidenceFile = evidenceFiles.get(idx);
				if (!(new File(baseDir, evidenceFile.getViewFile()).exists()))
					evidenceFile.setViewFile(null);
				continue;
			}
			String[] s = line.split(": ");
			String name = s[0].trim();
			String value = s[1].trim();
			if (value.length() == 0)
				continue;

			if (name.equalsIgnoreCase("Author"))
				name = "Autor";
			else if (name.equalsIgnoreCase("Last author"))
				name = "Último Autor";
			else if (name.equalsIgnoreCase("Last print date"))
				name = "Data da Última Impressão";
			else if (name.equalsIgnoreCase("Creation date"))
				name = "Data de Criação";
			else if (name.equalsIgnoreCase("Last save time"))
				name = "Data da Última Gravação";
			else if (name.equalsIgnoreCase("Company"))
				name = "Empresa";
			else
				continue; // Ignorar outras propriedades

			evidenceFile.addExtraProperty(category, new Property(name, value));
		}
	}

	/**
	 * Retorna a descrição curta.
	 */
	@Override
	public String getShortDescr() {
		return "Planilha do Excel";
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
