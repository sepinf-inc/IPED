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
			  String codePath = new File(url.toURI()).getAbsolutePath().replace("\\", "/");
			  //codePath = "E:/Imagens/18101.11/Pendrive/indexador/lib/Search.htm";
		      //codePath = "E:\\Imagens\\material_3106_2012\\indexador/lib/Search.htm";
		      //codePath = "E:/Casos/Teste/LAUDO 2191.11/indexador/lib/Search.htm";
		      //codePath = "E:/1-1973/indexador/lib/search.jar";
		      //codePath = "E:/nassif/rodrigo/M0410-16/indexador/lib/iped-search-app.jar";

		      codePath = codePath.substring(0, codePath.lastIndexOf('/'));
		      
		      if(casesPathFile == null)
		    	  casesPathFile = new File(codePath).getParentFile().getParentFile();
		      
		      File logParent = casesPathFile;
		      if(isMultiCase && casesPathFile.isFile())
		    	  logParent = casesPathFile.getParentFile();
		      
		      File logFile = new File(logParent, appLogFileName).getCanonicalFile();
		      LogConfiguration logConfiguration = new LogConfiguration(logFile);
		      logConfiguration.configureLogParameters(new File(codePath).getParentFile().getAbsolutePath(), nolog);
			  
		      App.get().getSearchParams().codePath = codePath;
			  App.get().init(logConfiguration, isMultiCase, casesPathFile);
			  
		  } catch (Exception e) {
			e.printStackTrace();
		  }
	      
	  }
	
}
