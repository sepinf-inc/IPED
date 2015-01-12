/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.SwingWorker;

import dpf.sp.gpinf.indexer.datasource.FTK1ReportProcessor;
import dpf.sp.gpinf.indexer.datasource.FTK3ReportProcessor;
import dpf.sp.gpinf.indexer.datasource.SleuthkitProcessor;
import dpf.sp.gpinf.indexer.index.IndexManager;
import dpf.sp.gpinf.indexer.index.ProgressFrame;
import dpf.sp.gpinf.indexer.parsers.OCRParser;

/*
 * Ponto de entrada do programa ao processar evidências.
 * Nome IndexFiles mantém compatibilidade com o AsAP.
 * TODO Manter apenas métodos utilizados pelo AsAP e separar demais funções em outra 
 * classe de entrada com nome mais intuitivo para execuções via linha de comando. 
 */
public class IndexFiles extends SwingWorker<Boolean, Integer> {

	/*
	 * command line parameters
	 */
	public boolean fromCmdLine = false;
	private boolean nogui = false;
	private boolean nologfile = false;
	public boolean verbose = false;
	public boolean appendIndex = false;

	private File palavrasChave;
	private List<File> reports;
	private File output;
	private File logFile;
	public String configPath;

	
	/*
	 * Nome dos casos fo FTK3+, necessário apenas em processamento de relatórios
	 */
	public List<String> caseNames;

	private PrintStream log, out, err;
	private ProgressFrame progressFrame;

	/*
	 * Última instância criada deta classe.
	 */
	private static IndexFiles lastInstance;

	/*
	 * Construtor utilizado pelo AsAP
	 */
	public IndexFiles(List<File> reports, File output, String configPath, File logFile, File keywordList) {
		this(reports, output, configPath, logFile, keywordList, null, null);
	}

	/*
	 * Construtor utilizado pelo AsAP
	 */
	public IndexFiles(List<File> reports, File output, String configPath, File logFile, File keywordList, List<String> bookmarksToOCR) {
		this(reports, output, configPath, logFile, keywordList, null, bookmarksToOCR);
	}

	/*
	 * Construtor utilizado pelo AsAP
	 */
	public IndexFiles(List<File> reports, File output, String configPath, File logFile, File keywordList, Boolean ignore, List<String> bookmarksToOCR) {
		super();
		this.reports = reports;
		this.output = output;
		this.palavrasChave = keywordList;
		this.configPath = configPath;
		this.logFile = logFile;
		this.caseNames = new ArrayList<String>();
		OCRParser.bookmarksToOCR = bookmarksToOCR;

		lastInstance = this;
	}

	/*
	 * Contrutor utilizado pela execução via linha de comando
	 */
	public IndexFiles(String[] args) {
		super();
		takeArgs(args);
		this.fromCmdLine = true;

		lastInstance = this;
	}

	/*
	 * Obtém a última instância criada
	 */
	public static IndexFiles getInstance() {
		return lastInstance;
	}

	/*
	 * Define o caminho onde será encontrado o arquivo de configuração principal.
	 */
	private void setConfigPath() throws Exception {
		String path = IndexFiles.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		path = URLDecoder.decode(path, "UTF-8");
		configPath = path.substring(0, path.lastIndexOf("/"));
		if (configPath.charAt(0) == '/' && configPath.charAt(2) == ':')
			configPath = configPath.substring(1);
	}

	/*
	 * Redireciona saída padrão e de erro para o arquivo de log informado.
	 * TODO Utilizar biblioteca de logging permitirá isolar saídas indesejadas de bibliotecas de terceiros,
	 * bem como definir o nível de log desejado.
	 */
	private void setLogFile(File logFile) throws Exception {
		if (logFile == null) {
			File logDir = new File(configPath + "/log");
			logDir.mkdir();
			logFile = new File(logDir, Versao.APP_EXT + "-" + (new Date()).getTime() + ".log");
		}

		out = System.out;
		err = System.err;
		log = new PrintStream(new FileOutputStream(logFile, true));
		System.setOut(log);
		System.setErr(log);
	}

	/*
	 * Fecha o arquivo de log, realizando flush automaticamente.
	 */
	private void closeLogFile() {
		log.close();
		System.setOut(out);
		System.setErr(err);
	}

	/*
	 * Imprime ajuda e aborta execução.
	 */
	private static void printUsageExit() {
		String usage = Versao.APP_NAME + "\n" + "Uso: java -jar indexer.jar -opcao  argumento [--opcao_sem_argumento]" + "\n" 
				+ "-r: \tpasta do relatorio do AsAP3 ou FTK3+" + "\n"
				+ "-d: \tfontes de dados diversas (pode ser utilizado varias vezes):" + "\n" 
					+ "\tdiretorio, imagem dd, 001, e01, aff (apenas linux), iso, disco físico ou arquivo iped (nesse caso os arquivos selecionados sao exportados e reindexados)" + "\n"
				+ "-o: \tpasta de saida da indexacao" + "\n" 
				+ "-c: \tnome do caso, necessário apenas para relatorio do FTK3+" + "\n"
				+ "-l: \tarquivo com lista de expressoes a serem exibidas na busca.\n\tExpressoes sem ocorrencias sao filtradas." + "\n"
				+ "-ocr: \taplica OCR apenas no bookmark informado.\n\t Pode ser utilizado multiplas vezes." + "\n" 
				+ "-log: \tEspecifica um arquivo de log diferente do padrao." + "\n"
				+ "--append: \tadiciona indexação a um indice ja existente" + "\n" 
				+ "--nogui: \tnao exibe a janela de progresso da indexacao" + "\n"
				+ "--nologfile: \timprime as mensagem de log na saida padrao" + "\n" 
				+ "--verbose: \tgera mensagens de log detalhadas, para debugar erros, porem diminui desempenho";

		System.out.println(usage);
		System.exit(1);
	}

	/*
	 * Interpreta parâmetros informados via linha de comando.
	 */
	private void takeArgs(String[] args) {
		if (args.length == 0 || args[0].contains("--help") || args[0].contains("/?") || args[0].contains("-h"))
			printUsageExit();

		File reportDir = null, aditionalDir = null, outputDir = null;
		reports = new ArrayList<File>();
		caseNames = new ArrayList<String>();
		OCRParser.bookmarksToOCR = new ArrayList<String>();

		for (int i = 0; i < args.length; i++) {
			if (args[i].compareTo("-r") == 0 && args.length > i + 1) {
				reportDir = new File(args[i + 1]);
				reports.add(reportDir);
				i++;
			} else if (args[i].compareTo("-d") == 0 && args.length > i + 1) {
				aditionalDir = new File(args[i + 1]);
				reports.add(aditionalDir);
				i++;
			} else if (args[i].compareTo("-c") == 0 && args.length > i + 1) {
				caseNames.add(args[i + 1]);
				i++;
			} else if (args[i].compareTo("-ocr") == 0 && args.length > i + 1) {
				OCRParser.bookmarksToOCR.add(args[i + 1]);
				i++;
			} else if (args[i].compareTo("-l") == 0 && args.length > i + 1) {
				palavrasChave = new File(args[i + 1]);
				i++;
			} else if (args[i].compareTo("-o") == 0 && args.length > i + 1) {
				outputDir = new File(args[i + 1]);
				i++;
			} else if (args[i].compareTo("-log") == 0 && args.length > i + 1) {
				logFile = new File(args[i + 1]);
				i++;
			} else if (args[i].compareTo("--nogui") == 0) {
				nogui = true;
			} else if (args[i].compareTo("--nologfile") == 0) {
				nologfile = true;
			} else if (args[i].compareTo("--verbose") == 0) {
				verbose = true;
			} else if (args[i].compareTo("--append") == 0) {
				appendIndex = true;
			} else
				printUsageExit();
		}

		if (reportDir == null || !(new File(reportDir, "files")).exists()) {
			if (reportDir == null || !(new File(reportDir, "Export")).exists())
				if (aditionalDir == null || (!aditionalDir.exists() && !SleuthkitProcessor.isPhysicalDrive(aditionalDir)))
					printUsageExit();
		} else if (caseNames.size() == 0)
			printUsageExit();

		if (outputDir != null)
			output = new File(outputDir, "indexador");
		else if (reportDir != null)
			output = new File(reportDir, "indexador");
		else
			output = new File(aditionalDir.getParentFile(), "indexador");

	}

	/*
	 * Realiza o processamento numa worker thread.
	 * 
	 * @see javax.swing.SwingWorker#doInBackground()
	 */
	@Override
	protected Boolean doInBackground() {
		try {

			if (fromCmdLine) {
				setConfigPath();
				if (!nologfile)
					setLogFile(logFile);
			} else if (!nologfile)
				setLogFile(logFile);

			System.out.println(new Date() + "\t[INFO]\t" + Versao.APP_NAME);

			if (!fromCmdLine)
				caseNames = FTK3ReportProcessor.getFTK3CaseNames(reports);

			Configuration.getConfiguration(configPath);

			IndexManager manager = new IndexManager(reports, caseNames, output, palavrasChave);
			manager.process();

			if (fromCmdLine)
				FTK1ReportProcessor.criarLinkBusca(output);

			this.firePropertyChange("mensagem", "", "Finalizado");
			System.out.println(new Date() + "\t[INFO]\t" + Versao.APP_EXT + " finalizado com sucesso.");

		} catch (Exception e) {
			System.err.print(new Date() + "\t[ERRO]\t");
			e.printStackTrace();
			if (!nologfile)
				closeLogFile();

			done = true;
			return false;
		}

		if (!nologfile)
			closeLogFile();

		done = true;
		success = true;
		return true;
	}

	/*
	 * Chamado após processamento para liberar recursos.
	 * 
	 * @see javax.swing.SwingWorker#done()
	 */
	@Override
	public void done() {
		if (progressFrame != null)
			progressFrame.dispose();
	}

	volatile boolean done = false, success = false;

	/*
	 * Executa o processamento com janela de progresso.
	 */
	public boolean executeWithProgressBar() {
		progressFrame = new ProgressFrame(this);
		this.addPropertyChangeListener(progressFrame);
		progressFrame.setVisible(true);
		return executar();
	}

	/*
	 * Executa o processamento sem janela de progresso.
	 * Chamado pelo AsAP.
	 */
	public boolean executar() {
		this.execute();
		while (!done)
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}

		return success;

	}

	/*
	 * Entrada principal da aplicação para processamento de evidências
	 */
	public static void main(String[] args) {

		IndexFiles indexador;
		indexador = new IndexFiles(args);
		boolean success;

		if (!indexador.nogui)
			success = indexador.executeWithProgressBar();
		else
			success = indexador.executar();

		if (!success)
			System.out.println("\nERRO!!!");
		else
			System.out.println("\n" + Versao.APP_EXT + " finalizado com sucesso.");

		if (!indexador.nologfile)
			System.out.println("Consulte o LOG na pasta \"Indexador/log\".");

		// PARA ASAP:
		// IndexFiles indexador = new IndexFiles(List<File> reports, File
		// output, String configPath, File logFile, File keywordList);
		// keywordList e logFile podem ser null. Nesse caso, o último é criado
		// na pasta log dentro de configPath

		// boolean success = indexador.executar();

	}

}