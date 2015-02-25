package gpinf.dev.preprocessor;

import gpinf.dev.data.CaseData;
import gpinf.dev.data.CaseInfo;
import gpinf.dev.data.EvidenceFile;
import gpinf.dev.data.FileGroup;
import gpinf.dev.data.PathNode;
import gpinf.dev.data.Property;
import gpinf.dev.filetypes.AttachmentFileType;
import gpinf.dev.filetypes.GenericFileType;
import gpinf.dev.filetypes.OriginalEmailFileType;
import gpinf.util.DateFormatException;
import gpinf.util.FileUtil;
import gpinf.util.HtmlUtil;
import gpinf.util.ParseUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dpf.sp.gpinf.indexer.IndexFiles;

/**
 * Implementação do processamento do relatório gerado pelo ASAP (que por sua vez
 * utiliza o relatório do FTK). Por ser um tratamento a relatório já existente,
 * cujo formato foi obtido através de engenharia reversa e não de uma
 * documentação apropriada, a implementação é extramamente dependente do formato
 * dos arquivos, com muitas strings fixas no código já que a criação de
 * constantes ou parâmetros prejudicaria a legibilidade do código e não
 * acrescentaria em flexibilidade a mudanças no formato original.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class AsapReportParser extends ReportParser {
	/** Arquivo de informações do caso. */
	private static final String CASE_INFOMATION_FILE = "CaseInformation.htm";

	/** Arquivo com lista de bookmarks. */
	private static final String BOOKMARK_FILE = "BookmarkContents.htm";

	/** Arquivo com conteúdo do report, incluindo lista de bookmarks. */
	private static final String CONTENTS_FILE = "contents.htm";

	/**
	 * Mapa "Identificador Único do Arquivo" (Utilizado o nome do arquivo
	 * exportado) X Objeto com dados do arquivo.
	 */
	private Map<String, EvidenceFile> filesMap = new HashMap<String, EvidenceFile>();

	// static DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT,
	// DateFormat.MEDIUM);

	/* Apenas conta o número de arquivos a indexar */
	private boolean countOnly = false;

	public AsapReportParser(boolean countOnly) {
		this.countOnly = countOnly;
	}

	/**
	 * Processa um relatório.
	 * 
	 * @param reportDir
	 *            diretório base do report
	 * @return dados do relatório processado
	 * @throws IOException
	 *             Indica que houve algum problema de leitura do arquivo
	 *             especificado.
	 * @throws ReportFormatException
	 *             Indica que o formato do relatório não é o esperado.
	 */
	@Override
	public CaseData parseReport(CaseData caseData, File reportDir) throws Exception {

		// if(countOnly) System.out.println(new
		// Date()+"\t[INFO]\t"+"Processando relatorio da pasta \"" +
		// reportDir.getAbsolutePath() + "\"");
		processCaseInformation(reportDir, caseData);
		// try {
		// processBookmarks(reportDir, caseData);
		// } catch (ReportFormatException rfe) {
		processBookmarksByContents(reportDir, caseData);
		// }
		for (FileGroup bookmark : caseData.getBookmarks()) {
			processBookmarkFiles(reportDir, caseData, bookmark);
		}
		// createTimeGroups(caseData);
		updateFolderTree(caseData);
		// processEvidenceFiles(reportDir, caseData);
		return caseData;
	}

	/**
	 * Processa o arquivo de informações do caso.
	 * 
	 * @param reportDir
	 *            diretório base do report
	 * @param caseData
	 *            dados do caso que irão receber as informações do relatório
	 *            processado
	 * @throws IOException
	 *             Erro na leitura do arquivo.
	 * @throws ReportFormatException
	 *             Indica que o formato do relatório não é o esperado.
	 */
	private void processCaseInformation(File reportDir, CaseData caseData) throws IOException, ReportFormatException {
		CaseInfo caseInformation = caseData.getCaseInformation();
		File file = new File(reportDir, CASE_INFOMATION_FILE);
		if (countOnly)
			System.out.println(new Date() + "\t[INFO]\t" + "Processando " + file.getName());
		String str = FileUtil.read(file);

		// Layout esperado: Tabela, sendo a primeira linha com o Cabeçalho e uma
		// imagem; linhas com duas células, uma o nome de propriedade e outra o
		// valor.
		String s = str.toLowerCase();
		int p1 = s.indexOf("<table");
		if (p1 < 0)
			throw new ReportFormatException("Tag de inicio da tabela não encontrada no arquivo " + file.getAbsolutePath());
		p1 = s.indexOf(">", p1);
		if (p1 < 0)
			throw new ReportFormatException("Fechamento da tag de inicio da tabela não encontrada no arquivo " + file.getAbsolutePath());
		int p2 = s.indexOf("</table", p1);
		if (p2 < 0)
			throw new ReportFormatException("Tag de fim da tabela não encontrada no arquivo " + file.getAbsolutePath());
		List<List<String>> table = HtmlUtil.extractCellsFromTable(str.substring(p1 + 1, p2));

		// Primeira linha é o cabeçalho
		if (table.size() > 0 && table.get(0).size() > 0) {
			String header = table.get(0).get(0);
			s = header.toLowerCase();
			p1 = s.indexOf("<img");
			if (p1 >= 0) {
				p2 = s.indexOf("/>", p1);
				if (p2 < 0) {
					p2 = s.indexOf("/img>", p1);
					if (p2 >= 0)
						p2 += 5;
				} else {
					p2 += 2;
				}
				if (p2 >= 0) {
					int p3 = s.indexOf("src=\"", p1);
					if (p3 >= 0) {
						int p4 = s.indexOf("\"", p3 + 5);
						caseInformation.setHeaderImage(header.substring(p3 + 5, p4));

						String hdr = header.substring(0, p1) + header.substring(p2);
						while (hdr.startsWith("<br>"))
							hdr = hdr.substring(4);
						while (hdr.startsWith("<br/>"))
							hdr = hdr.substring(5);
						while (hdr.startsWith("<br />"))
							hdr = hdr.substring(6);
						caseInformation.setHeaderText(hdr);
					}
				}
			}
		}
		if (caseInformation.getHeaderText() == null)
			throw new ReportFormatException("Cabeçalho não encontrado no arquivo " + file.getAbsolutePath());

		// Restante são as "propriedades"
		for (int i = 1; i < table.size(); i++) {
			List<String> cells = table.get(i);
			if (cells.size() < 2)
				throw new ReportFormatException("Linha com menos de duas células no arquivo " + file.getAbsolutePath() + ". Células:" + cells);
			// Ignora linha de "ATENÇÃO"
			if (cells.get(0).toLowerCase().indexOf("atenção") >= 0)
				continue;
			caseInformation.addProperty(new Property(cells.get(0), cells.get(1)));
		}
	}

	/**
	 * Processa o arquivo com bookmarks do caso.
	 * 
	 * @param reportDir
	 *            diretório base do report
	 * @param caseData
	 *            dados do caso que irão receber as informações do relatório
	 *            processado
	 * @throws IOException
	 *             Erro na leitura do arquivo.
	 * @throws ReportFormatException
	 *             Indica que o formato do relatório não é o esperado.
	 */
	private void processBookmarks(File reportDir, CaseData caseData) throws IOException, ReportFormatException {
		File file = new File(reportDir, BOOKMARK_FILE);
		String str = FileUtil.read(file);
		if (countOnly)
			System.out.println(new Date() + "\t[INFO]\t" + "Processando " + file.getName());
		caseData.bookmarks = new ArrayList<FileGroup>();

		// Layout esperado: Categoria, composta de pagina HTML, nome e
		// descrição.
		String s = str.toLowerCase();
		int p1 = 0;
		while (true) {
			p1 = s.indexOf("categoria:", p1);
			if (p1 < 0)
				break;
			p1 = s.indexOf("href=\"", p1);
			if (p1 < 0)
				throw new ReportFormatException("Arquivo de Bookmark não encontrado no arquivo " + file.getAbsolutePath());
			p1 += 6;
			int p2 = s.indexOf("\">", p1);
			if (p2 < 0)
				throw new ReportFormatException("Término da tag de arquivo de Bookmark não encontrado no arquivo " + file.getAbsolutePath());
			String bmkFile = str.substring(p1, p2);

			int p3 = s.indexOf("</", p2);
			if (p3 < 0)
				throw new ReportFormatException("Tag de fim do nome do Bookmark não encontrada no arquivo " + file.getAbsolutePath());
			String bmkName = str.substring(p2 + 2, p3).trim();

			int p4 = s.indexOf("descrição: ", p3);
			if (p4 < 0)
				throw new ReportFormatException("Descrição do Bookmark '" + bmkName + "' não encontrada no arquivo " + file.getAbsolutePath());

			int p5 = s.indexOf("<br>", p4);
			if (p5 < 0)
				throw new ReportFormatException("Fim da descrição do Bookmark '" + bmkName + "' não encontrada no arquivo " + file.getAbsolutePath());
			String bmkDesc = str.substring(p4 + 11, p5).trim();
			caseData.addBookmark(new FileGroup(bmkName, bmkDesc, bmkFile));

			p1 = p5;
		}
	}

	/**
	 * Processa o arquivo com lista de bookmarks do caso. Método alternativo
	 * para quando o processBookmarks falhar.
	 * 
	 * @param reportDir
	 *            diretório base do report
	 * @param caseData
	 *            dados do caso que irão receber as informações do relatório
	 *            processado
	 * @throws IOException
	 *             Erro na leitura do arquivo.
	 * @throws ReportFormatException
	 *             Indica que o formato do relatório não é o esperado.
	 */
	private void processBookmarksByContents(File reportDir, CaseData caseData) throws IOException, ReportFormatException {
		File file = new File(reportDir, CONTENTS_FILE);
		String str = FileUtil.read(file);
		if (countOnly)
			System.out.println(new Date() + "\t[INFO]\t" + "Processando " + file.getName());
		caseData.bookmarks = new ArrayList<FileGroup>();

		// Layout esperado: Categoria, composta de pagina HTML, nome e
		// descrição.
		String s = str.toLowerCase();
		int p1 = s.indexOf("lista por categoria");
		int pEnd = s.indexOf("<p>", p1);
		while (true) {
			p1 = s.indexOf("href=\"", p1);
			if (p1 > pEnd)
				break;
			if (p1 < 0)
				break;
			p1 += 6;
			int p2 = s.indexOf("\"", p1);
			if (p2 < 0)
				throw new ReportFormatException("Término da tag de arquivo de Bookmark não encontrado no arquivo " + file.getAbsolutePath());
			String bmkFile = str.substring(p1, p2);

			p2 = s.indexOf(">", p2);
			int p3 = s.indexOf("</", p2);
			if (p3 < 0)
				throw new ReportFormatException("Tag de fim do nome do Bookmark não encontrada no arquivo " + file.getAbsolutePath());
			String bmkName = str.substring(p2 + 1, p3).trim();

			caseData.addBookmark(new FileGroup(bmkName, "", bmkFile));

			p1 = p3;
		}
	}

	/**
	 * Processa páginas com arquivos associados a um bookmark.
	 * 
	 * @param reportDir
	 *            diretório base do report
	 * @param caseData
	 *            dados do caso que irão receber as informações do relatório
	 *            processado
	 * @param bookmark
	 *            bookmark a ser processado
	 * @throws IOException
	 *             Erro na leitura do arquivo.
	 * @throws ReportFormatException
	 *             Indica que o formato do relatório não é o esperado.
	 */
	private void processBookmarkFiles(File reportDir, CaseData caseData, FileGroup bookmark) throws Exception {
		int i = 1;
		PathNode root = caseData.getRootNode();
		while (true) {
			// Monta o nome do arquivo (tratando _N para quando houver mais de
			// um arquivo
			String fileName = bookmark.getFileName();
			if (i > 1) {
				int pos = fileName.lastIndexOf('.');
				if (pos < 0)
					break;
				fileName = fileName.substring(0, pos) + "_" + i + fileName.substring(pos);
			}
			File file = new File(reportDir, fileName);
			if (!file.exists()) {
				if (i == 1)
					throw new FileNotFoundException(new Date() + "\t[AVISO]\t" + "Arquivo não encontrado: " + file.getAbsolutePath());
				break;
			}
			if (countOnly)
				System.out.println(new Date() + "\t[INFO]\t" + "Processando " + file.getName());

			// Lê e interpreta o arquivo
			String str = FileUtil.read(file);
			String lower = str.toLowerCase();
			String separator = "<hr>";

			int p1 = lower.indexOf(separator);
			if (p1 < 0)
				throw new ReportFormatException("Nenhum item localizado no arquivo " + file.getAbsolutePath());
			while (true) {
				if (Thread.interrupted())
					throw new InterruptedException(Thread.currentThread().getName() + "interrompida.");

				int p2 = lower.indexOf(separator, p1 + separator.length());
				if (p2 < 0)
					break;

				EvidenceFile evidenceFile = new EvidenceFile();
				String pathEmailPai = "";
				String[] lines = str.substring(p1 + separator.length(), p2).split("<br>");
				for (String line : lines) {
					int p3 = line.indexOf(':');
					if (p3 < 0) {
						if (line.startsWith("&nbsp;&nbsp;&nbsp;&nbsp;<"))
							addAttachment(reportDir, root, line, evidenceFile, caseData, bookmark);
						
						else if (line.contains("Arquivo originalmente apagado e recuperado"))
							evidenceFile.setDeleted(true);
						
						continue;
					}
					int p4 = line.indexOf(">");
					p4 = (p4 < p3 && p4 > 0) ? (p4 + 1) : 0;
					String name = line.substring(p4, p3);
					String value = line.substring(p3 + 1).trim();
					if (value.startsWith("<b>") && value.endsWith("</b>"))
						value = value.substring(3, value.length() - 4);
					if (value.length() == 0)
						continue;
					if (value.toLowerCase().indexOf(">não disponível<") >= 0)
						continue;
					value = HtmlUtil.removeSpecialChars(value);

					if (name.equalsIgnoreCase("Anexos")) {
						if (!value.startsWith("Exportado como:"))
							addAttachment(reportDir, root, line, evidenceFile, caseData, bookmark);
						continue;
					}
					if (name.equalsIgnoreCase("Email de origem")) {
						pathEmailPai = value.substring(value.indexOf("]  ") + 3);
						continue;
					}
					if (name.equalsIgnoreCase("Email de origem exportado como")) {
						addEmailPai(reportDir, root, line, pathEmailPai, evidenceFile, caseData, bookmark);
						continue;
					}

					try {
						// Tratamento das propreidades "básicas"
						if (name.equalsIgnoreCase("Caminho completo")) {
							if (!countOnly)
								evidenceFile.setPathNode(root.addNewPath(value));
						} else if (name.equalsIgnoreCase("Caminho")) {
							if (!countOnly)
								evidenceFile.setPathNode(root.addNewPath(value + File.separatorChar + evidenceFile.getName()));
						} else if (name.equalsIgnoreCase("Arquivo"))
							evidenceFile.setName(value);
						else if (name.equalsIgnoreCase("Data da última modificação"))
							evidenceFile.setModificationDate(ParseUtil.parseDate(value));
						else if (name.equalsIgnoreCase("Data de criação"))
							evidenceFile.setCreationDate(ParseUtil.parseDate(value));
						else if (name.equalsIgnoreCase("Data do último acesso"))
							evidenceFile.setAccessDate(ParseUtil.parseDate(value));
						else if (name.equalsIgnoreCase("Tamanho lógico (em bytes)"))
							evidenceFile.setLength(ParseUtil.parseLong(value));
						else if (name.equalsIgnoreCase("Exportado como")) {
							String[] links = HtmlUtil.getLinks(value);
							if (links.length > 0) {
								evidenceFile.setExportedFile(links[0]);
								if (links.length > 1)
									evidenceFile.setAlternativeFile(links[1]);
								File exportFile = new File(reportDir, evidenceFile.getFileToIndex());
								if (exportFile.exists())
									evidenceFile.setLength(exportFile.length());

								EvidenceFile knownFile = filesMap.get(evidenceFile.getExportedFile());
								if (knownFile != null) {
									fillProperties(knownFile, evidenceFile);
									evidenceFile = knownFile;
								}
							}
						} else if (name.equalsIgnoreCase("Tipo de arquivo")) {
							evidenceFile.setType(new GenericFileType(value));
							
						} else {
							// Tratamento do restante das propriedades
							//evidenceFile.addProperty(new Property(name, value));
						}
					} catch (DateFormatException dfe) {
						throw new ReportFormatException(dfe.getMessage());
					} catch (NumberFormatException nfe) {
						throw new ReportFormatException(nfe.getMessage());
					}
				}
				if (evidenceFile.getName() == null && evidenceFile.getPathNode() != null) {
					// evidenceFile.setName(new
					// PathNode("").addNewPath(evidenceFile.getPathString()).toString());
					evidenceFile.setName(evidenceFile.getPathNode().toString());
				}

				// Checa se propriedades mínimas estão presentes
				if (/*
					 * evidenceFile.getName() == null || evidenceFile.getType()
					 * == null ||
					 */evidenceFile.getExportedFile() == null) {
					System.err.println(new Date() + "\t[AVISO]\t" + "ARQUIVO IGNORADO ('Exportado Como' é obrigatório): " + evidenceFile.getName());
					/*
					 * for (int j = 0; j < lines.length; j++) {
					 * System.err.println("\t" + lines[j]); }
					 */
				} else
					addEvidenceFile(evidenceFile, caseData, bookmark, true);
				p1 = p2;
			}

			// Incrementa o contador de arquivos
			i++;
		}
	}

	private void addAttachment(File reportDir, PathNode root, String line, EvidenceFile evidenceFile, CaseData caseData, FileGroup bookmark) throws InterruptedException {
		line = HtmlUtil.removeSpecialChars(line);
		EvidenceFile anexo = new EvidenceFile();
		anexo.setName(HtmlUtil.getAttachName(line));
		if (evidenceFile.getPath() != null)
			if (!countOnly)
				anexo.setPathNode(root.addNewPath(evidenceFile.getPath() + ">>" + anexo.getName()));
		anexo.setType(new AttachmentFileType());
		anexo.setAccessDate(evidenceFile.getAccessDate());
		anexo.setCreationDate(evidenceFile.getCreationDate());
		anexo.setModificationDate(evidenceFile.getModDate());
		String[] links = HtmlUtil.getLinks(line);
		if (links.length > 0) {
			anexo.setExportedFile(links[0].trim());
			if (links.length > 1)
				anexo.setAlternativeFile(links[1].trim());
			File attachFile = new File(reportDir, anexo.getExportedFile());
			anexo.setLength(attachFile.length());

			EvidenceFile knownFile = filesMap.get(anexo.getExportedFile());
			if (knownFile != null)
				knownFile.setEmailPai(evidenceFile);
			else
				anexo.setEmailPai(evidenceFile);
			// evidenceFile.addAttachment(anexo);

			addEvidenceFile(anexo, caseData, bookmark, false);
		} else
			System.err.println(new Date() + "\t[AVISO]\t" + "ARQUIVO IGNORADO (anexo de email não exportado):\n" + line + "\n");
	}

	private void addEmailPai(File reportDir, PathNode root, String line, String pathEmailPai, EvidenceFile evidenceFile, CaseData caseData, FileGroup bookmark) throws InterruptedException {
		line = HtmlUtil.removeSpecialChars(line);
		EvidenceFile emailPai = new EvidenceFile();
		int index = pathEmailPai.lastIndexOf(">>");
		if (index != -1)
			emailPai.setName(pathEmailPai.substring(index + 2));
		if (!countOnly)
			emailPai.setPathNode(root.addNewPath(pathEmailPai));
		emailPai.setType(new OriginalEmailFileType());
		emailPai.setAccessDate(evidenceFile.getAccessDate());
		emailPai.setCreationDate(evidenceFile.getCreationDate());
		emailPai.setModificationDate(evidenceFile.getModDate());
		String[] links = HtmlUtil.getLinks(line);
		if (links.length > 0) {
			emailPai.setExportedFile(links[0].trim());
			if (links.length > 1)
				emailPai.setAlternativeFile(links[1].trim());
			File emailPaiFile = new File(reportDir, emailPai.getExportedFile());
			emailPai.setLength(emailPaiFile.length());

			EvidenceFile knownFile = filesMap.get(emailPai.getExportedFile());
			if (knownFile != null)
				evidenceFile.setEmailPai(knownFile);
			else
				evidenceFile.setEmailPai(emailPai);
			// emailPai.addAttachment(evidenceFile);

			addEvidenceFile(emailPai, caseData, bookmark, false);
		} else
			System.err.println(new Date() + "\t[AVISO]\t" + "ARQUIVO IGNORADO (email de origem não exportado):\n" + line + "\n");
	}

	private void fillProperties(EvidenceFile knownFile, EvidenceFile evidenceFile) {
		// se knownFile for anexo ou email de origem, seta novamente suas
		// propriedades
		if (AttachmentFileType.class.isInstance(knownFile.getType()) || OriginalEmailFileType.class.isInstance(knownFile.getType())) {
			if (evidenceFile.getName() != null)
				knownFile.setName(evidenceFile.getName());
			if (evidenceFile.getPathNode() != null)
				knownFile.setPathNode(evidenceFile.getPathNode());
			if (evidenceFile.getType() != null)
				knownFile.setType(evidenceFile.getType());
			if (evidenceFile.getAccessDate() != null)
				knownFile.setAccessDate(evidenceFile.getAccessDate());
			if (evidenceFile.getCreationDate() != null)
				knownFile.setCreationDate(evidenceFile.getCreationDate());
			if (evidenceFile.getModDate() != null)
				knownFile.setModificationDate(evidenceFile.getModDate());
			if (evidenceFile.getLength() != null)
				knownFile.setLength(evidenceFile.getLength());
			if (evidenceFile.getAlternativeFile() != null)
				knownFile.setAlternativeFile(evidenceFile.getAlternativeFile());
		}

	}

	private void addEvidenceFile(EvidenceFile evidenceFile, CaseData caseData, FileGroup bookmark, boolean addBookmark) throws InterruptedException {
		// Verifica se é um arquivo já conhecido
		if (!filesMap.containsKey(evidenceFile.getExportedFile())) {
			if (!countOnly) {
				if (addBookmark)
					evidenceFile.addCategory(bookmark.getName());

				/*
				 * String alternativeFile = evidenceFile.getAlternativeFile();
				 * if(alternativeFile != ""){
				 * evidenceFile.setAlternativeFile(null); EvidenceFile
				 * alternativeEvidence = evidenceFile.clone();
				 * alternativeEvidence.setId(evidenceFile.getId());
				 * alternativeEvidence.setExportedFile(alternativeFile);
				 * filesMap.put(alternativeEvidence.getExportedFile(),
				 * alternativeEvidence);
				 * caseData.addEvidenceFile(alternativeEvidence); }
				 */

				filesMap.put(evidenceFile.getExportedFile(), evidenceFile);
				caseData.addEvidenceFile(evidenceFile);

			} else {
				filesMap.put(evidenceFile.getExportedFile(), null);
				caseData.incDiscoveredEvidences(1);
				caseData.incDiscoveredVolume(evidenceFile.getLength());
				IndexFiles.getInstance().firePropertyChange("discovered", 0, caseData.getDiscoveredEvidences());
			}

		} else if (!countOnly) {
			// Apenas acrescenta o arquivo já conhecido no bookmark
			// bookmark.addEvidenceFile(knownFile);
			EvidenceFile knownFile = filesMap.get(evidenceFile.getExportedFile());
			if (addBookmark) {
				boolean added = false;
				for (String bookname : knownFile.getCategorySet())
					if (bookname.equals(bookmark.getName()))
						added = true;
				if (!added)
					knownFile.addCategory(bookmark.getName());
			}

			fillProperties(knownFile, evidenceFile);

			if (knownFile.getEmailPai() == null && evidenceFile.getEmailPai() != null)
				knownFile.setEmailPai(evidenceFile.getEmailPai());

		}
	}
}
