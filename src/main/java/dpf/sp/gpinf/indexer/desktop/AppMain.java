package dpf.sp.gpinf.indexer.desktop;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.LogConfiguration;
import dpf.sp.gpinf.indexer.Versao;
import dpf.sp.gpinf.indexer.process.Manager;
import dpf.sp.gpinf.indexer.util.CustomLoader;

public class AppMain {
	
	private static final String appLogFileName = "IPED-SearchApp.log";
	private static final int MIN_JAVA_VER = 8;
	private static final int MAX_JAVA_VER = 9;
	
	File casePath;
	//File casePath = new File("E:\\1-pchp-3.13-blind");
	
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
		
		casePath = detectCasePath();
		
		start(casePath, null, args);
	}
	
	private File detectCasePath() {
		URL url = AppMain.class.getProtectionDomain().getCodeSource().getLocation();
		File jarFile = null;
		try {
			if(url.toURI().getAuthority() == null)
				  jarFile = new File(url.toURI());
			  else
				  jarFile = new File(url.toURI().getSchemeSpecificPart());
			
			return jarFile.getParentFile().getParentFile().getParentFile();
			
		}catch(Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private void loadArgs(String[] args) {
		if(args == null)
			return;
		
		for(int i = 0; i < args.length; i++){
			if(args[i].equals("--nologfile"))
				  nolog = true;
			if(args[i].equals("-multicases")){
				  isMultiCase = true;
				  casesPathFile = new File(args[i + 1]).getAbsoluteFile();
					
				  if(!casesPathFile.exists()){
					  System.out.println("Arquivo de casos inexistente: " + args[1]);
					  System.exit(1);
				  }
			}
		}
	}
		  
	public void start(File casePath, Manager processingManager, String[] args) {
		
		  try {
			  boolean fromCustomLoader = CustomLoader.isFromCustomLoader(args);
			  if(fromCustomLoader)
			      args = CustomLoader.clearCustomLoaderArgs(args);
			  
			  loadArgs(args);
			  
			  File libDir = new File(new File(casePath, "indexador"), "lib");
		      if(casesPathFile == null)
		    	  casesPathFile = casePath;
		      
		      File logParent = casesPathFile;
		      if(isMultiCase && casesPathFile.isFile())
		    	  logParent = casesPathFile.getParentFile();
		      
		      File logFile = new File(logParent, appLogFileName).getCanonicalFile();
		      LogConfiguration logConfiguration = null;
		      
		      if(processingManager == null){
		    	  logConfiguration = new LogConfiguration(libDir.getParentFile().getAbsolutePath(), logFile);
		    	  logConfiguration.configureLogParameters(nolog, fromCustomLoader);
		    	  
		    	  Logger LOGGER = LoggerFactory.getLogger(IndexFiles.class);
			      if(!fromCustomLoader)
			    	  LOGGER.info(Versao.APP_NAME);
			      
			      Configuration.getConfiguration(libDir.getParentFile().getAbsolutePath());
		      }
		      
		      if(!fromCustomLoader && processingManager == null) {
		            List<File> jars = new ArrayList<File>();
		            if(Configuration.optionalJarDir != null && Configuration.optionalJarDir.listFiles() != null)
		            	jars.addAll(Arrays.asList(Configuration.optionalJarDir.listFiles()));
		            jars.add(Configuration.tskJarFile);
		            
		            String[] customArgs = CustomLoader.getCustomLoaderArgs(this.getClass().getName(), args, logFile);
		            
		            CustomLoader.run(customArgs, jars);
		            return;
		            
		        }else{
		        	App.get().getSearchParams().codePath = libDir.getAbsolutePath();
					App.get().init(logConfiguration, isMultiCase, casesPathFile, processingManager);
					  
					InicializarBusca init = new InicializarBusca(App.get().getSearchParams(), processingManager);
					init.execute();
		        }
			  
		  } catch (Exception e) {
			e.printStackTrace();
		  }
	      
	  }
	
}
