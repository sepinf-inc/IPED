package dpf.sp.gpinf.indexer.desktop;

import java.io.File;
import java.net.URL;

import dpf.sp.gpinf.indexer.LogConfiguration;

public class AppMain {
	
	private static final String appLogFileName = "IPED-SearchApp.log";

	public static void main(String[] args) {
		  
		boolean isMultiCase = false;
		boolean nolog = false;
		File casesPathFile = null;
		
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
		
		  URL url = AppMain.class.getProtectionDomain().getCodeSource().getLocation();
		  try {
			  File jarFile;
			  if(url.toURI().getAuthority() == null)
				  jarFile = new File(url.toURI());
			  else
				  jarFile = new File(url.toURI().getSchemeSpecificPart());
			  
			  //Caso para teste
		      //jarFile = new File("\\\\10.11.15.57\\gpinf\\Peritos\\nassif.lfcn\\1-test6/indexador/lib/iped-search-app.jar");
			  
			  File libDir = jarFile.getParentFile();
		      if(casesPathFile == null)
		    	  casesPathFile = libDir.getParentFile().getParentFile();
		      
		      File logParent = casesPathFile;
		      if(isMultiCase && casesPathFile.isFile())
		    	  logParent = casesPathFile.getParentFile();
		      
		      File logFile = new File(logParent, appLogFileName).getCanonicalFile();
		      LogConfiguration logConfiguration = new LogConfiguration(logFile);
		      logConfiguration.configureLogParameters(libDir.getParentFile().getAbsolutePath(), nolog);
			  
		      App.get().getSearchParams().codePath = libDir.getAbsolutePath();
			  App.get().init(logConfiguration, isMultiCase, casesPathFile);
			  
		  } catch (Exception e) {
			e.printStackTrace();
		  }
	      
	  }
	
}
