package dpf.sp.gpinf.indexer.desktop;

import java.io.File;
import java.net.URL;

import dpf.sp.gpinf.indexer.LogConfiguration;
import dpf.sp.gpinf.indexer.process.Manager;

public class AppMain {
	
	private static final String appLogFileName = "IPED-SearchApp.log";
	
	boolean isMultiCase = false;
	boolean nolog = false;
	File casesPathFile = null;

	public static void main(String[] args) {
		new AppMain().start(args);
	}
	
	private void start(String[] args) {	
		for(int i = 0; i < args.length; i++){
			if(args[i].equals("--nologfile"))
				  nolog = true;
			if(args[i].equals("-multicases")){
				  isMultiCase = true;
				  casesPathFile = new File(args[i + 1]).getAbsoluteFile();
					
				  if(!casesPathFile.exists()){
					  System.out.println("Arquivo de casos inexistente: " + args[1]);
					  return;
				  }
			}
		}
		start(null, null);
	}
		  
	public void start(File casePath, Manager processingManager) {	
		
		  URL url = AppMain.class.getProtectionDomain().getCodeSource().getLocation();
		  try {
			  File jarFile;
			  if(url.toURI().getAuthority() == null)
				  jarFile = new File(url.toURI());
			  else
				  jarFile = new File(url.toURI().getSchemeSpecificPart());
			  
			  //Caso para teste
		      //jarFile = new File("E:\\1-process-comp9/indexador/lib/iped-search-app.jar");
			  
		      if(casePath != null)
				  jarFile = new File(casePath, "indexador/lib/iped-search-app.jar");
			  
			  File libDir = jarFile.getParentFile();
		      if(casesPathFile == null)
		    	  casesPathFile = libDir.getParentFile().getParentFile();
		      
		      File logParent = casesPathFile;
		      if(isMultiCase && casesPathFile.isFile())
		    	  logParent = casesPathFile.getParentFile();
		      
		      File logFile = new File(logParent, appLogFileName).getCanonicalFile();
		      LogConfiguration logConfiguration = null;
		      if(processingManager == null){
		    	  logConfiguration = new LogConfiguration(logFile);
		    	  logConfiguration.configureLogParameters(libDir.getParentFile().getAbsolutePath(), nolog);
		      }
			  
		      App.get().getSearchParams().codePath = libDir.getAbsolutePath();
			  App.get().init(logConfiguration, isMultiCase, casesPathFile, processingManager);
			  
			  InicializarBusca init = new InicializarBusca(App.get().getSearchParams(), processingManager);
			  init.execute();
			  
		  } catch (Exception e) {
			e.printStackTrace();
		  }
	      
	  }
	
}
