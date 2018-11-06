/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de EvidÃªncias Digitais (IPED).
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
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.tika.fork.ForkParser2;
import org.apache.tika.mime.MimeTypesFactory;

import dpf.sp.gpinf.indexer.config.AdvancedIPEDConfig;
import dpf.sp.gpinf.indexer.config.ConfigurationDirectoryImpl;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.IPEDConfig;
import dpf.sp.gpinf.indexer.config.LocalConfig;
import dpf.sp.gpinf.indexer.config.LocaleConfig;
import dpf.sp.gpinf.indexer.config.OCRConfig;
import dpf.sp.gpinf.indexer.config.PDFToImageConfig;
import dpf.sp.gpinf.indexer.config.PluginConfig;
import dpf.sp.gpinf.indexer.config.SleuthKitConfig;
import dpf.sp.gpinf.indexer.config.UFEDReaderConfig;
import dpf.sp.gpinf.indexer.parsers.EDBParser;
import dpf.sp.gpinf.indexer.parsers.IndexDatParser;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.parsers.LibpffPSTParser;
import dpf.sp.gpinf.indexer.parsers.RegistryParser;
import dpf.sp.gpinf.indexer.parsers.external.ExternalParsersFactory;
import dpf.sp.gpinf.indexer.process.task.VideoThumbTask;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import dpf.sp.gpinf.indexer.util.Util;

/**
 * Classe principal de carregamento e acesso às configurações da aplicação.
 */
public class Configuration {

	static Configuration singleton;

	public static Configuration getInstance() {
		if(singleton==null)  singleton = new Configuration();
		return singleton;
	}

	private Configuration() {
		props=new UTF8Properties();
		props.put(IPEDConfig.CONFDIR, appRoot+"\\conf");
	}

	ConfigurationDirectoryImpl configDirectory;
	UTF8Properties props;
	PluginConfig pluginConfig;
	IPEDConfig ipedConfig;
  
	
  public static final String CONFIG_FILE = "IPEDConfig.txt"; //$NON-NLS-1$
  public static final String LOCAL_CONFIG = "LocalConfig.txt"; //$NON-NLS-1$
  public static final String EXTRA_CONFIG_FILE = "AdvancedConfig.txt"; //$NON-NLS-1$
  public static final String PARSER_CONFIG = "ParserConfig.xml"; //$NON-NLS-1$
  public static final String EXTERNAL_PARSERS = "ExternalParsers.xml"; //$NON-NLS-1$
  public static final String CUSTOM_MIMES_CONFIG = "CustomSignatures.xml"; //$NON-NLS-1$
  
  public static UTF8Properties properties = new UTF8Properties();
  //private static File indexerTemp;
  public static String configPath, appRoot;
  public static File optionalJarDir;
  public static File tskJarFile;
  public static String loaddbPathWin;
  
  private static AtomicBoolean loaded = new AtomicBoolean();
  
  private static String getAppRoot(String configPath){
	  String appRoot = new File(configPath).getAbsolutePath();
	  if(appRoot.contains("profiles")) //$NON-NLS-1$
	   	appRoot = new File(appRoot).getParentFile().getParentFile().getParent();
	  return appRoot;
  }
  
  /**
   * Configurações a partir do caminho informado.
   */
  public static void getConfiguration(String configPathStr) throws Exception {
	  if(loaded.getAndSet(true))
		  return;

    configPath = configPathStr;

    appRoot = getAppRoot(configPath);

    System.setProperty("tika.config", configPath + "/conf/" + PARSER_CONFIG); //$NON-NLS-1$ //$NON-NLS-2$
    System.setProperty(ExternalParsersFactory.EXTERNAL_PARSER_PROP, configPath + "/conf/" + EXTERNAL_PARSERS); //$NON-NLS-1$
    System.setProperty(MimeTypesFactory.CUSTOM_MIMES_SYS_PROP, appRoot + "/conf/" + Configuration.CUSTOM_MIMES_CONFIG); //$NON-NLS-1$
    
    System.setProperty("iped.configPath", configPath );

    properties.load(new File(appRoot + "/" + LOCAL_CONFIG)); //$NON-NLS-1$
    properties.load(new File(configPath + "/" + CONFIG_FILE)); //$NON-NLS-1$
    properties.load(new File(configPath + "/conf/" + EXTRA_CONFIG_FILE)); //$NON-NLS-1$

    String optional_jars = properties.getProperty("optional_jars"); //$NON-NLS-1$
    if(optional_jars != null) {
        optionalJarDir = new File(appRoot + "/" + optional_jars.trim()); //$NON-NLS-1$
        ForkParser2.plugin_dir = optionalJarDir.getCanonicalPath();
    }

    String regripperFolder = properties.getProperty("regripperFolder"); //$NON-NLS-1$
    if(regripperFolder != null)
        System.setProperty(RegistryParser.TOOL_PATH_PROP, appRoot + "/" + regripperFolder.trim()); //$NON-NLS-1$
    
    properties.put(IPEDConfig.CONFDIR, appRoot+"/conf");
    getInstance().props=properties;
  }

  void loadLibs(File indexerTemp) throws IOException {
	    if (System.getProperty("os.name").toLowerCase().startsWith("windows")) { //$NON-NLS-1$ //$NON-NLS-2$

	      String arch = "x86"; //$NON-NLS-1$
	      if(System.getProperty("os.arch").contains("64")) //$NON-NLS-1$ //$NON-NLS-2$
	    	  arch = "x64"; //$NON-NLS-1$

	      loaddbPathWin = appRoot + "/tools/tsk/" + arch + "/tsk_loaddb"; //$NON-NLS-1$ //$NON-NLS-2$

	      File nativelibs = new File(loaddbPathWin).getParentFile().getParentFile();
	      nativelibs = new File(nativelibs, arch);

	      IOUtil.copiaDiretorio(nativelibs, new File(indexerTemp, "nativelibs"), true); //$NON-NLS-1$
	      Util.loadNatLibs(new File(indexerTemp, "nativelibs")); //$NON-NLS-1$

	      System.setProperty(OCRParser.TOOL_PATH_PROP, appRoot + "/tools/tesseract"); //$NON-NLS-1$
	      System.setProperty(EDBParser.TOOL_PATH_PROP, appRoot + "/tools/esedbexport/"); //$NON-NLS-1$
	      System.setProperty(LibpffPSTParser.TOOL_PATH_PROP, appRoot + "/tools/pffexport/"); //$NON-NLS-1$
	      System.setProperty(IndexDatParser.TOOL_PATH_PROP, appRoot + "/tools/msiecfexport/"); //$NON-NLS-1$

	      String mplayerPath = properties.getProperty("mplayerPath"); //$NON-NLS-1$
	      if(mplayerPath != null)
	          VideoThumbTask.mplayerWin = mplayerPath.trim();

	    }else{
	    	String tskJarPath = properties.getProperty("tskJarPath"); //$NON-NLS-1$
	    	if (tskJarPath != null && !tskJarPath.isEmpty())
	        	tskJarPath = tskJarPath.trim();
	    	else
	    		throw new IPEDException("You must set tskJarPath on LocalConfig.txt!"); //$NON-NLS-1$

	    	tskJarFile = new File(tskJarPath);
	    	if(!tskJarFile.exists())
	    		throw new IPEDException("File not found " + tskJarPath + ". Set tskJarPath on LocalConfig.txt!"); //$NON-NLS-1$ //$NON-NLS-2$
	    }
  }

  public void loadExtensionConfigurables() throws IOException {
	    configDirectory = new ConfigurationDirectoryImpl(Paths.get(props.getProperty(IPEDConfig.CONFDIR)));
	    configDirectory.addPath(Paths.get(appRoot+"\\"+PluginConfig.LOCAL_CONFIG));

	    ConfigurationManager pluginConfigManager = new ConfigurationManager(configDirectory);

	    pluginConfig = new PluginConfig();
	    pluginConfigManager.addObject(pluginConfig);

	    pluginConfigManager.loadConfigs();
  }

  public LocaleConfig loadLocaleConfigurable() throws IOException {
	    configDirectory = new ConfigurationDirectoryImpl(Paths.get(props.getProperty(IPEDConfig.CONFDIR)));
	    configDirectory.addPath(Paths.get(appRoot+"\\"+PluginConfig.LOCAL_CONFIG));

	    ConfigurationManager localeConfigManager = new ConfigurationManager(configDirectory);

	    LocaleConfig localeConfig = new LocaleConfig();
	    localeConfigManager.addObject(localeConfig);

	    localeConfigManager.loadConfigs();

	    return localeConfig;
  }
  
  public PluginConfig getPluginConfig() {
	  return pluginConfig;
  }

  public void loadConfigurables() throws IOException {
	    configDirectory = new ConfigurationDirectoryImpl(Paths.get(props.getProperty(IPEDConfig.CONFDIR)));
	    configDirectory.addPath(Paths.get(appRoot+"\\"+IPEDConfig.CONFIG_FILE));
	    configDirectory.addPath(Paths.get(appRoot+"\\"+PluginConfig.LOCAL_CONFIG));

	    ConfigurationManager configManager = new ConfigurationManager(configDirectory);

	    AdvancedIPEDConfig advancedConfig = new AdvancedIPEDConfig();
	    configManager.addObject(advancedConfig);

	    IPEDConfig ipedConfig = new IPEDConfig();
	    configManager.addObject(ipedConfig);

	    PluginConfig pluginConfig = new PluginConfig();
	    configManager.addObject(ipedConfig);

	    LocalConfig localConfig = new LocalConfig();
	    configManager.addObject(localConfig);

	    LocaleConfig localeConfig = new LocaleConfig();
	    configManager.addObject(localeConfig);

	    OCRConfig ocrConfig = new OCRConfig();
	    configManager.addObject(ocrConfig);

	    PDFToImageConfig pdfToImageConfig = new PDFToImageConfig();
	    configManager.addObject(pdfToImageConfig);

	    SleuthKitConfig sleuthKitConfig = new SleuthKitConfig();
	    configManager.addObject(sleuthKitConfig);

	    UFEDReaderConfig urConfig = new UFEDReaderConfig();
	    configManager.addObject(urConfig);

	    //adiciona os jars dos plugins como fonte para busca de arquivos de configuração
	    if(pluginConfig!=null) {
		    File[] jars = pluginConfig.getOptionalJars(appRoot);
		    if(jars != null) {
		    	for(File jar : jars) {
		    	    if(jar.getName().endsWith(".jar")) {
		    			try {
		    				configDirectory.addZip(jar.toPath());
						} catch (IOException e) {
							e.printStackTrace();
						}
		    		}

		    		if(jar.isDirectory()) {
		    			configDirectory.addPath(jar.toPath());
		    		}
		    	}
		    }
	    }

	    configManager.loadConfigs();
	    
	    loadLibs(localConfig.getIndexerTemp());
  }

  public static void setConfigPath(String configPath2) {
	  Configuration.configPath = configPath2; 
	  Configuration.appRoot = getAppRoot(configPath);
  }
}
