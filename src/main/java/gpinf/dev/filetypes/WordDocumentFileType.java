package gpinf.dev.filetypes;

import gpinf.dev.DEVConstants;
import gpinf.dev.data.EvidenceFile;
import gpinf.dev.data.Property;
import gpinf.util.ProcessUtil;

import java.io.File;
import java.util.List;

/**
 * Implementação da classe utilizada para arquivos do Microsoft Word(DOC).
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public abstract class WordDocumentFileType extends EvidenceFileType {
	/** Identificador de serialização. */
	private static final long serialVersionUID = -5066816042653544717L;

	/**
	 * Ícone associado ao tipo. * private static final Icon icon =
	 * IconUtil.createIcon("ft-word");
	 * 
	 * /** Processa arquivos deste tipo. Passa uma lista de arquivos para um
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

		String result = ProcessUtil.run(DEVConstants.DOC2PDF, sb.toString());
		if (result == null)
			return;

		// Trata resultado, processando as propriedades
		String[] lines = result.split("\n");
		String category = "Propriedades do Documento";
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
			else if (name.equalsIgnoreCase("Title"))
				name = "Título";
			else if (name.equalsIgnoreCase("Last author"))
				name = "Último Autor";
			else if (name.equalsIgnoreCase("Revision number"))
				name = "Número de Revisões";
			else if (name.equalsIgnoreCase("Last print date"))
				name = "Data da Última Impressão";
			else if (name.equalsIgnoreCase("Creation date"))
				name = "Data de Criação";
			else if (name.equalsIgnoreCase("Last save time"))
				name = "Data da Última Gravação";
			else if (name.equalsIgnoreCase("Number of pages"))
				name = "Número de Páginas";
			else if (name.equalsIgnoreCase("Number of words"))
				name = "Número de Palavras";
			else if (name.equalsIgnoreCase("Number of characters"))
				name = "Número de Caracteres";
			else if (name.equalsIgnoreCase("Number of paragraphs"))
				name = "Número de Parágrafos";
			else if (name.equalsIgnoreCase("Company"))
				name = "Empresa";
			else
				continue; // Ignorar outras propriedades

			evidenceFile.addExtraProperty(category, new Property(name, value));
		}
	}

	/**
	 * Retorna a descrição curta padrão.
	 */
	@Override
	public String getShortDescr() {
		return "Documento do Word";
	}

	/**
	 * Retorna o tipo de visulização que deve ser utilizado pelo visualizador
	 * para este tipo de arquivo.
	 * 
	 * @return Tipo de visualização "Pdf".
	 * 
	 *         public ViewType getViewType() { return ViewType.PDF; }
	 * 
	 *         /** Retorna o ícone correspondente ao tipo de arquivo. * public
	 *         Icon getIcon() { return icon; }
	 */
}
