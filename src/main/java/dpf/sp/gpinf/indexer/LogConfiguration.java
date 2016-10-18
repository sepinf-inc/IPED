package dpf.sp.gpinf.indexer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.slf4j.bridge.SLF4JBridgeHandler;

import dpf.sp.gpinf.indexer.util.FilterOutputStream;

public class LogConfiguration {
	
	File logFile;
	private PrintStream log, out, err;
	private SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
	
	public LogConfiguration(File logFile){
		this.logFile = logFile;
	}
	
	private void setConsoleLogFile(File logFile) throws FileNotFoundException {
		logFile.getParentFile().mkdirs();
	    out = System.out;
	    err = System.err;
	    FileOutputStream fos = new FileOutputStream(logFile, true);
	    log = new PrintStream(new FilterOutputStream(fos, out, "IPED"));
	    System.setOut(log);
	    System.setErr(log);
	  }

	  /**
	   * Fecha o arquivo de log, realizando flush automaticamente.
	   */
	  public void closeConsoleLogFile() {
	    if (log != null) {
	      log.close();
	    }
	    if (out != null) {
	      System.setOut(out);
	    }
	    if (err != null) {
	      System.setErr(err);
	    }
	  }

	  public void configureLogParameters(String configPath, boolean noLog) throws FileNotFoundException, MalformedURLException {
	    
		System.setProperty("logFileDate", df.format(new Date()));
	    if (noLog) {
	      System.setProperty("log4j.configurationFile", new File(configPath, "conf/Log4j2ConfigurationConsoleOnly.xml").toURI().toURL().toString());
	    } else {
	      if (logFile == null)
	    	  logFile = new File(configPath, "log/IPED-" + df.format(new Date()) + ".log");
	      
	      System.setProperty("logFileNamePath", logFile.getPath());
	      System.setProperty("log4j.configurationFile", new File(configPath, "conf/Log4j2ConfigurationFile.xml").toURI().toURL().toString());
	      setConsoleLogFile(logFile);
	    }
	    // instala bridge para capturar logs gerados pelo java.util.logging
	    SLF4JBridgeHandler.removeHandlersForRootLogger();
	    SLF4JBridgeHandler.install();

	    //instancia o logger
	    LogManager.getRootLogger();
	  }
}
