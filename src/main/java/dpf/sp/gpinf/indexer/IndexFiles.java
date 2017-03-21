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
import java.net.URL;
import java.util.List;

import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.process.Manager;
import dpf.sp.gpinf.indexer.process.ProgressConsole;
import dpf.sp.gpinf.indexer.process.ProgressFrame;
import dpf.sp.gpinf.indexer.process.task.KFFTask;
import dpf.sp.gpinf.indexer.util.IPEDException;

/**
 * Ponto de entrada do programa ao processar evidências. Nome IndexFiles mantém compatibilidade com
 * o AsAP. TODO Manter apenas métodos utilizados pelo AsAP e separar demais funções em outra classe
 * de entrada com nome mais intuitivo para execuções via linha de comando.
 */
public class IndexFiles extends SwingWorker<Boolean, Integer> {

  private static Logger LOGGER = null;
  /**
   * command line parameters
   */
  public boolean fromCmdLine = false;
  public boolean appendIndex = false;

  String configPath;
  boolean nogui = false;
  boolean nologfile = false;
  File palavrasChave;
  List<File> dataSource;
  File output;

  File logFile;
  LogConfiguration logConfiguration;

  private CmdLineArgs cmdLineParams;

  private ProgressFrame progressFrame;
  
  private Manager manager;
  
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
	  URL url = IndexFiles.class.getProtectionDomain().getCodeSource().getLocation();
	  //configPath = System.getProperty("user.dir");
	  configPath = new File(url.toURI()).getParent();
	  
	  if(cmdLineParams.getCmdArgs().containsKey("-profile")){
		  String profile = cmdLineParams.getCmdArgs().get("-profile").get(0);
		  configPath = new File(configPath, "profiles/" + profile).getAbsolutePath();
		  if(!new File(configPath).exists())
			  throw new IPEDException("Profile informado inexistente!");
	  }
  }

  /**
   * Importa base de hashes no formato NSRL.
   *
   * @param kffPath caminho para base de hashes.
   */
  void importKFF(String kffPath) {
    try {
      setConfigPath();
      Configuration.getConfiguration(configPath);
      KFFTask kff = new KFFTask(null);
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
      if (fromCmdLine) 
    	  setConfigPath();
    	
      logConfiguration = new LogConfiguration(logFile);
      logConfiguration.configureLogParameters(configPath, nologfile);
      
      LOGGER = LoggerFactory.getLogger(IndexFiles.class);
      
      if(nogui){
    	 ProgressConsole console = new ProgressConsole();
    	 this.addPropertyChangeListener(console);
      }

      LOGGER.info(Versao.APP_NAME);

      Configuration.getConfiguration(configPath);

      manager = new Manager(dataSource, output, palavrasChave);
      cmdLineParams.saveIntoCaseData(manager.getCaseData());
      manager.process();

      this.firePropertyChange("mensagem", "", "Finalizado");
      LOGGER.info("{} finalizado.", Versao.APP_EXT);
      success = true;

    } catch (Throwable e) {
      success = false;
      if(e instanceof IPEDException)
    	  LOGGER.error("Erro no processamento: " + e.getMessage());
      else
    	  LOGGER.error("Erro no processamento:", e);

    } finally {
      if(manager != null)
       	manager.setProcessingFinished(true);
      if(manager == null || !manager.isSearchAppOpen())
    	logConfiguration.closeConsoleLogFile();
      done = true;
    }

    return success;
  }

  /**
   * Chamado após processamento para liberar recursos.
   *
   * @see javax.swing.SwingWorker#done()
   */
  @Override
  public void done() {
    if (progressFrame != null) {
      progressFrame.dispose();
    }
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
   * Executa o processamento sem janela de progresso. Chamado pelo AsAP.
   */
  public boolean executar() {
    this.execute();
    /*try {
     return this.get();
		
     } catch (InterruptedException | ExecutionException e) {
     //e.printStackTrace();
     return false;
     }
     * */
    while (!done) {
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e1) {
        e1.printStackTrace();
      }
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

    if (!indexador.nogui) {
      success = indexador.executeWithProgressBar();
    } else {
      success = indexador.executar();
    }

    if (!success) {
    	getInstance().logConfiguration.getSystemOut().println("\nERRO!!!");
    } else {
    	getInstance().logConfiguration.getSystemOut().println("\n" + Versao.APP_EXT + " finalizado.");
    }

    if (!indexador.nologfile) {
    	getInstance().logConfiguration.getSystemOut().println("Consulte o LOG na pasta \"IPED/log\".");
    }
    
    if(getInstance().manager == null || !getInstance().manager.isSearchAppOpen())
    	System.exit((success)?0:1);

    // PARA ASAP:
    // IndexFiles indexador = new IndexFiles(List<File> reports, File
    // output, String configPath, File logFile, File keywordList);
    // keywordList e logFile podem ser null. Nesse caso, o último é criado
    // na pasta log dentro de configPath
    // boolean success = indexador.executar();
  }

}
