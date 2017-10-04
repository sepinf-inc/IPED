package dpf.sp.gpinf.indexer.desktop;

import java.io.File;
import java.net.URL;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import dpf.sp.gpinf.indexer.LogConfiguration;
import dpf.sp.gpinf.indexer.process.Manager;

public class AppMain {
	
	private static final String appLogFileName = "IPED-SearchApp.log";
	private static final int MIN_JAVA_VER = 8;
	private static final int MAX_JAVA_VER = 9;
	
	boolean isMultiCase = false;
	boolean nolog = false;
	File casesPathFile = null;

	public static void main(String[] args) {
	    
	    checkJavaVersion();
		new AppMain().start(args);
	}
	
	private static void checkJavaVersion(){
	    try {
            SwingUtilities.invokeAndWait(new Runnable(){
                  @Override
                  public void run(){
                      String versionStr = System.getProperty("java.version");
                      if(versionStr.startsWith("1."))
                          versionStr = versionStr.substring(2, 3);
                      int version = Integer.valueOf(versionStr);
                      
                      if(version < MIN_JAVA_VER){
                          JOptionPane.showMessageDialog(App.get(), 
                              "É necessário atualizar o Java para a versão " + MIN_JAVA_VER + " ou superior!", 
                              "Erro na inicialização", JOptionPane.ERROR_MESSAGE);
                          System.exit(1);
                      }
                      if(version > MAX_JAVA_VER){
                          JOptionPane.showMessageDialog(App.get(), 
                              "Java versão " + version + " não testado, podem ocorrer erros inesperados!", 
                              "Alerta na inicialização", JOptionPane.WARNING_MESSAGE);
                      }
                  }
              });
        } catch (Exception e) {
            e.printStackTrace();
        }
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
		      //jarFile = new File("E:\\1-pchp-3.13-blind/indexador/lib/iped-search-app.jar");
			  
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
		    	  logConfiguration = new LogConfiguration(libDir.getParentFile().getAbsolutePath(), logFile);
		    	  logConfiguration.configureLogParameters(nolog, true);
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
