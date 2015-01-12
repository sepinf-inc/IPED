package gpinf.dev.preprocessor;

import gpinf.dev.data.CaseData;

import java.io.File;
import java.io.IOException;

/**
 * Classe abstrata que define um processador de relatórios.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public abstract class ReportParser {
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
	public abstract CaseData parseReport(CaseData caseData, File reportDir) throws Exception;

	/**
	 * Processa os arquivos do caso, fazendo as conversões de formato
	 * necessárias e extraindo informações adicionais dos arquivos de evidência.
	 * A execução deste método é feita em lotes, seperando os arquivos a serem
	 * preocessados pelo seu tipo.
	 * 
	 * @param reportDir
	 *            diretório base do report
	 * @param caseData
	 *            dados do caso que irão receber as informações do caso
	 *            processado
	 * @throws IOException
	 *             Erro na leitura do arquivo.
	 */
	protected void processEvidenceFiles(File reportDir, CaseData caseData) throws IOException {
		/*
		 * List<EvidenceFile> evidenceFiles = caseData.getEvidenceFiles(); int
		 * num = evidenceFiles.size(); String pl = (num != 1 ? "s" : "");
		 * System.out.println("Processando por tipo o" + pl + " " + num +
		 * " arquivo" + pl); boolean[] processed = new boolean[num]; for (int i
		 * = 0; i < num; i++) { if (processed[i]) continue; List<EvidenceFile>
		 * sublist = new ArrayList<EvidenceFile>(); EvidenceFileType efti =
		 * evidenceFiles.get(i).getType(); Class<? extends EvidenceFileType> ci
		 * = efti.getClass(); for (int j = i; j < num; j++) { if (processed[j])
		 * continue; EvidenceFile ej = evidenceFiles.get(j); Class<? extends
		 * EvidenceFileType> cj = ej.getType().getClass(); if (ci.equals(cj)) {
		 * sublist.add(ej); processed[j] = true; } } System.out.println("    " +
		 * ci.getSimpleName() + " (" + sublist.size() + " arquivo" +
		 * (sublist.size() != 1 ? "s" : "") + ")"); try { Method mainMethod =
		 * ci.getMethod("processFiles", new Class[] {File.class, List.class});
		 * mainMethod.invoke(efti, reportDir, sublist); } catch (Exception e) {
		 * e.printStackTrace(); } }
		 */
	}

	/**
	 * Cria grupo de arquivos por data. Inicialmente utiliza um grupo por mês e
	 * considera a data de última modificação do arquivo se esta estiver
	 * presente, senão utiliza a data de criação.
	 * 
	 * @param caseData
	 *            dados do caso
	 */
	/*
	 * protected void createTimeGroups(CaseData caseData) { SortedMap<Integer,
	 * FileGroup> map = new TreeMap<Integer, FileGroup>(); Calendar calendar =
	 * Calendar.getInstance(); DateFormat df = new
	 * SimpleDateFormat("MMMM/yyyy"); for (EvidenceFile evidenceFile :
	 * caseData.getEvidenceFiles()) { Date date =
	 * evidenceFile.getModificationDate(); if (date == null) date =
	 * evidenceFile.getCreationDate(); int month = Integer.MAX_VALUE; if (date
	 * != null) { calendar.setTime(date); month = calendar.get(Calendar.MONTH) +
	 * calendar.get(Calendar.YEAR) * 12; } FileGroup fg = map.get(month); if (fg
	 * == null) { fg = new FileGroup((date == null) ? "Data Indefinida" :
	 * df.format(date), "", ""); map.put(month, fg); }
	 * fg.addEvidenceFile(evidenceFile); } for (int month : map.keySet()) {
	 * caseData.addTimeGroup(map.get(month)); } }
	 */

	/**
	 * Atualiza árvore de diretórios do caso;
	 * 
	 * @param caseData
	 *            dados do caso
	 */
	protected void updateFolderTree(CaseData caseData) {
		caseData.getRootNode().buildChildArray();
	}
}
