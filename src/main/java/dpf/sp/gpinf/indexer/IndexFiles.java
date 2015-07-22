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
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.process.Manager;
import dpf.sp.gpinf.indexer.process.ProgressFrame;
import dpf.sp.gpinf.indexer.process.task.KFFTask;

/**
 * Ponto de entrada do programa ao processar evidências.
 * Nome IndexFiles mantém compatibilidade com o AsAP.
 * TODO Manter apenas métodos utilizados pelo AsAP e separar demais funções em outra 
 * classe de entrada com nome mais intuitivo para execuções via linha de comando. 
 */
public class IndexFiles extends SwingWorker<Boolean, Integer> {

	/**
	 * command line parameters
	 */
	public boolean fromCmdLine = false;
	public boolean verbose = false;
	public boolean appendIndex = false;
	
	String configPath;
	boolean nogui = false;
	boolean nologfile = false;
	File palavrasChave;
	List<File> dataSource;
	File output;
	File logFile;
	
	private CmdLineArgs cmdLineParams;
	
	/**
	 * Nome dos casos fo FTK3+, necessário apenas em processamento de relatórios
	 */
	public List<String> caseNames;

	private PrintStream log, out, err;
	private ProgressFrame progressFrame;

	/**
	 * Última instância criada deta classe.
	 */
	private static IndexFiles lastInstance;

	/**
	 * Construtor utilizado pelo AsAP
	 */
	public IndexFiles(List<File> reports, File output, String configPath, File logFile, File keywordList) {
		this(reports, output, configPath, logFile, keywordList, null, null);
	}

	/**
	 * Construtor utilizado pelo AsAP
	 */
	public IndexFiles(List<File> reports, File output, String configPath, File logFile, File keywordList, List<String> bookmarksToOCR) {
		this(reports, output, configPath, logFile, keywordList, null, bookmarksToOCR);
	}

	/**
	 * Construtor utilizado pelo AsAP
	 */
	public IndexFiles(List<File> reports, File output, String configPath, File logFile, File keywordList, Boolean ignore, List<String> bookmarksToOCR) {
		super();
		lastInstance = this;
		this.dataSource = reports;
		this.output = output;
		this.palavrasChave = keywordList;
		this.configPath = configPath;
		this.logFile = logFile;
		this.caseNames = new ArrayList<String>();
		OCRParser.bookmarksToOCR = bookmarksToOCR;		
	}

	/**
	 * Contrutor utilizado pela execução via linha de comando
	 */
	public IndexFiles(String[] args) {
		super();
		lastInstance = this;
		cmdLineParams = new CmdLineArgs();
		cmdLineParams.takeArgs(args);
		this.fromCmdLine = true;
	}

	/**
	 * Obtém a última instância criada
	 */
	public static IndexFiles getInstance() {
		return lastInstance;
	}

	/**
	 * Define o caminho onde será encontrado o arquivo de configuração principal.
	 */
	private void setConfigPath() throws Exception {
		String path = IndexFiles.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		path = URLDecoder.decode(path, "UTF-8");
		configPath = path.substring(0, path.lastIndexOf("/"));
		if (configPath.charAt(0) == '/' && configPath.charAt(2) == ':')
			configPath = configPath.substring(1);
	}

	/**
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

	/**
	 * Fecha o arquivo de log, realizando flush automaticamente.
	 */
	private void closeLogFile() {
		log.close();
		System.setOut(out);
		System.setErr(err);
	}
	
	/**
	 * Importa base de hashes no formato NSRL.
	 * 
	 * @param kffPath caminho para base de hashes.
	 */
	void importKFF(String kffPath){
	    try {
            setConfigPath();
            Configuration.getConfiguration(configPath);
            KFFTask kff = new KFFTask();
            kff.init(Configuration.properties, null);
            kff.importKFF(new File(kffPath));
        } catch (Exception e) {
            e.printStackTrace();
        }
	}

	/**
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
				caseNames = FTK3ReportProcessor.getFTK3CaseNames(dataSource);

			Configuration.getConfiguration(configPath);

			Manager manager = new Manager(dataSource, caseNames, output, palavrasChave);
			cmdLineParams.saveIntoCaseData(manager.getCaseData());
			manager.process();

			if (fromCmdLine)
				FTK1ReportProcessor.criarLinkBusca(output);

			this.firePropertyChange("mensagem", "", "Finalizado");
			System.out.println(new Date() + "\t[INFO]\t" + Versao.APP_EXT + " finalizado com sucesso.");

		} catch (Throwable e) {
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

	/**
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

	/**
	 * Executa o processamento com janela de progresso.
	 */
	public boolean executeWithProgressBar() {
		progressFrame = new ProgressFrame(this);
		this.addPropertyChangeListener(progressFrame);
		progressFrame.setVisible(true);
		return executar();
	}

	/**
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

	/**
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
