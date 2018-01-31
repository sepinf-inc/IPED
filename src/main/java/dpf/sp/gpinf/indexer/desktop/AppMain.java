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
	
	private static final String appLogFileName = "IPED-SearchApp.log"; //$NON-NLS-1$
	private static final int MIN_JAVA_VER = 8;
	private static final int MAX_JAVA_VER = 9;
	
	File casePath;
	//File casePath = new File("F:\\pedo-3.13.3-test");
	
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
                      String versionStr = System.getProperty("java.version"); //$NON-NLS-1$
                      if(versionStr.startsWith("1.")) //$NON-NLS-1$
                          versionStr = versionStr.substring(2, 3);
                      int dotIdx = versionStr.indexOf("."); //$NON-NLS-1$
                      if(dotIdx > -1)
                    	  versionStr = versionStr.substring(0, dotIdx);
                      int version = Integer.valueOf(versionStr);
                      
                      if(version < MIN_JAVA_VER){
                          JOptionPane.showMessageDialog(App.get(), 
                              Messages.getString("AppMain.javaVerError.1") + MIN_JAVA_VER + Messages.getString("AppMain.javaVerError.2"),  //$NON-NLS-1$ //$NON-NLS-2$
                              Messages.getString("AppMain.error.Title"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
                          System.exit(1);
                      }
                      if(version > MAX_JAVA_VER){
                          JOptionPane.showMessageDialog(App.get(), 
                              Messages.getString("AppMain.javaVerWarn.1") + version + Messages.getString("AppMain.javaVerWarn.2"),  //$NON-NLS-1$ //$NON-NLS-2$
                              Messages.getString("AppMain.warn.Title"), JOptionPane.WARNING_MESSAGE); //$NON-NLS-1$
                      }
                  }
              });
        } catch (Exception e) {
            e.printStackTrace();
        }
	}
	
	private void start(String[] args) {	
		
		if(casePath == null)
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
			if(args[i].equals("--nologfile")) //$NON-NLS-1$
				  nolog = true;
			if(args[i].equals("-multicases")){ //$NON-NLS-1$
				  isMultiCase = true;
				  casesPathFile = new File(args[i + 1]).getAbsoluteFile();
					
				  if(!casesPathFile.exists()){
					  System.out.println(Messages.getString("AppMain.NoCasesFile") + args[1]); //$NON-NLS-1$
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
			  
			  File libDir = new File(new File(casePath, "indexador"), "lib"); //$NON-NLS-1$ //$NON-NLS-2$
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
