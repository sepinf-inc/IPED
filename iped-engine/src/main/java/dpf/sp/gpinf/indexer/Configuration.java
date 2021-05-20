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

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.NoOpLog;
import org.apache.tika.fork.ForkParser2;
import org.apache.tika.mime.MimeTypesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.inc.sepinf.python.PythonParser;
import dpf.sp.gpinf.indexer.config.AdvancedIPEDConfig;
import dpf.sp.gpinf.indexer.config.AudioTranscriptConfig;
import dpf.sp.gpinf.indexer.config.CategoryConfig;
import dpf.sp.gpinf.indexer.config.CategoryToExpandConfig;
import dpf.sp.gpinf.indexer.config.CategoryToExportConfig;
import dpf.sp.gpinf.indexer.config.ConfigurationDirectory;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.DocThumbTaskConfig;
import dpf.sp.gpinf.indexer.config.ElasticSearchTaskConfig;
import dpf.sp.gpinf.indexer.config.HashTaskConfig;
import dpf.sp.gpinf.indexer.config.HtmlReportTaskConfig;
import dpf.sp.gpinf.indexer.config.IPEDConfig;
import dpf.sp.gpinf.indexer.config.ImageThumbTaskConfig;
import dpf.sp.gpinf.indexer.config.LocalConfig;
import dpf.sp.gpinf.indexer.config.LocaleConfig;
import dpf.sp.gpinf.indexer.config.OCRConfig;
import dpf.sp.gpinf.indexer.config.PDFToImageConfig;
import dpf.sp.gpinf.indexer.config.PluginConfig;
import dpf.sp.gpinf.indexer.config.SleuthKitConfig;
import dpf.sp.gpinf.indexer.config.UFEDReaderConfig;
import dpf.sp.gpinf.indexer.parsers.EDBParser;
import dpf.sp.gpinf.indexer.parsers.IndexDatParser;
import dpf.sp.gpinf.indexer.parsers.LibpffPSTParser;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.parsers.RegistryParser;
import dpf.sp.gpinf.indexer.parsers.external.ExternalParser;
import dpf.sp.gpinf.indexer.parsers.external.ExternalParsersFactory;
import dpf.sp.gpinf.indexer.process.task.VideoThumbTask;
import dpf.sp.gpinf.indexer.util.CustomLoader.CustomURLClassLoader;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.configuration.IConfigurationDirectory;

/**
 * Classe principal de carregamento e acesso às configurações da aplicação.
 */
public class Configuration {

    public static final String CONFIG_FILE = "IPEDConfig.txt"; //$NON-NLS-1$
    public static final String LOCAL_CONFIG = "LocalConfig.txt"; //$NON-NLS-1$
    public static final String EXTRA_CONFIG_FILE = "AdvancedConfig.txt"; //$NON-NLS-1$
    public static final String PARSER_CONFIG = "ParserConfig.xml"; //$NON-NLS-1$
    public static final String EXTERNAL_PARSERS = "ExternalParsers.xml"; //$NON-NLS-1$
    public static final String CUSTOM_MIMES_CONFIG = "CustomSignatures.xml"; //$NON-NLS-1$

    private static Configuration singleton;
    private static AtomicBoolean loaded = new AtomicBoolean();

    ConfigurationDirectory configDirectory;
    public Logger logger;
    public UTF8Properties properties = new UTF8Properties();
    public String configPath, appRoot;
    public File optionalJarDir;
    public File tskJarFile;
    public String loaddbPathWin;

    public static Configuration getInstance() {
        if (singleton == null) {
            synchronized (Configuration.class) {
                if (singleton == null)
                    singleton = new Configuration();
            }
        }
        return singleton;
    }

    private Configuration() {
    }

    private String getAppRoot(String configPath) {
        String appRoot = new File(configPath).getAbsolutePath();
        if (appRoot.contains("profiles")) //$NON-NLS-1$
            appRoot = new File(appRoot).getParentFile().getParent();
        return appRoot;
    }

    private void configureLogger(String configPath) {
        // DataSource.testConnection(configPathStr);
        LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", NoOpLog.class.getName()); //$NON-NLS-1$

        logger = null;
        if (Configuration.class.getClassLoader().getClass().getName().equals(CustomURLClassLoader.class.getName())) {
            logger = LoggerFactory.getLogger(Configuration.class);
            logger.info("Loading configuration from " + configPath); //$NON-NLS-1$
        }
    }

    /**
     * Configurações a partir do caminho informado.
     */
    public void getConfiguration(String configPathStr) throws IOException {

        configPath = configPathStr;

        if (appRoot == null) {
            appRoot = getAppRoot(configPath);
        }

        configureLogger(configPath);

        System.setProperty(IConfigurationDirectory.IPED_ROOT, appRoot);
        System.setProperty(ExternalParser.EXTERNAL_PARSERS_ROOT, appRoot);
        System.setProperty("tika.config", configPath + "/conf/" + PARSER_CONFIG); //$NON-NLS-1$ //$NON-NLS-2$
        System.setProperty(ExternalParsersFactory.EXTERNAL_PARSER_PROP, configPath + "/conf/" + EXTERNAL_PARSERS); //$NON-NLS-1$
        System.setProperty(MimeTypesFactory.CUSTOM_MIMES_SYS_PROP,
                appRoot + "/conf/" + Configuration.CUSTOM_MIMES_CONFIG); //$NON-NLS-1$
        System.setProperty(PythonParser.PYTHON_PARSERS_FOLDER, appRoot + "/conf/parsers");
        System.setProperty(IConfigurationDirectory.IPED_CONF_PATH, configPath);

        properties.load(new File(appRoot + "/" + LOCAL_CONFIG)); //$NON-NLS-1$
        properties.load(new File(configPath + "/" + CONFIG_FILE)); //$NON-NLS-1$
        properties.load(new File(configPath + "/conf/" + EXTRA_CONFIG_FILE)); //$NON-NLS-1$

        String optional_jars = properties.getProperty("optional_jars"); //$NON-NLS-1$
        if (optional_jars != null) {
            optionalJarDir = new File(appRoot + "/" + optional_jars.trim()); //$NON-NLS-1$
            ForkParser2.plugin_dir = optionalJarDir.getCanonicalPath();
        }

        String regripperFolder = properties.getProperty("regripperFolder"); //$NON-NLS-1$
        if (regripperFolder != null)
            System.setProperty(RegistryParser.TOOL_PATH_PROP, appRoot + "/" + regripperFolder.trim()); //$NON-NLS-1$

        properties.put(IPEDConfig.CONFDIR, configPath + "/conf");
    }

    public void loadLibsAndToolPaths() throws IOException {
        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) { //$NON-NLS-1$ //$NON-NLS-2$

            String arch = "x86"; //$NON-NLS-1$
            if (System.getProperty("os.arch").contains("64")) //$NON-NLS-1$ //$NON-NLS-2$
                arch = "x64"; //$NON-NLS-1$

            loaddbPathWin = appRoot + "/tools/tsk/" + arch + "/tsk_loaddb"; //$NON-NLS-1$ //$NON-NLS-2$

            File nativelibs = new File(loaddbPathWin).getParentFile().getParentFile();
            nativelibs = new File(nativelibs, arch);

            if (System.getProperty("ipedNativeLibsLoaded") == null) { //$NON-NLS-1$
                Util.loadNatLibs(nativelibs);
                System.setProperty("ipedNativeLibsLoaded", "true"); //$NON-NLS-1$ //$NON-NLS-2$
            }

            System.setProperty(OCRParser.TOOL_PATH_PROP, appRoot + "/tools/tesseract"); //$NON-NLS-1$
            System.setProperty(EDBParser.TOOL_PATH_PROP, appRoot + "/tools/esedbexport/"); //$NON-NLS-1$
            System.setProperty(LibpffPSTParser.TOOL_PATH_PROP, appRoot + "/tools/pffexport/"); //$NON-NLS-1$
            System.setProperty(IndexDatParser.TOOL_PATH_PROP, appRoot + "/tools/msiecfexport/"); //$NON-NLS-1$

            String mplayerPath = properties.getProperty("mplayerPath"); //$NON-NLS-1$
            if (mplayerPath != null)
                VideoThumbTask.mplayerWin = mplayerPath.trim();

        } else {
            String tskJarPath = properties.getProperty("tskJarPath"); //$NON-NLS-1$
            if (tskJarPath != null && !tskJarPath.isEmpty())
                tskJarPath = tskJarPath.trim();
            else
                throw new IPEDException("You must set tskJarPath on LocalConfig.txt!"); //$NON-NLS-1$

            tskJarFile = new File(tskJarPath);
            if (!tskJarFile.exists())
                throw new IPEDException("File not found " + tskJarPath + ". Set tskJarPath on LocalConfig.txt!"); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    public void loadConfigurables(String configPathStr) throws IOException {
        loadConfigurables(configPathStr, false);
    }

    public void loadConfigurables(String configPathStr, boolean loadAll) throws IOException {

        if (loaded.getAndSet(true))
            return;

        getConfiguration(configPathStr);

        configDirectory = new ConfigurationDirectory(Paths.get(properties.getProperty(IPEDConfig.CONFDIR)));
        configDirectory.addPath(Paths.get(configPath + "/" + CONFIG_FILE));
        configDirectory.addPath(Paths.get(appRoot + "/" + LOCAL_CONFIG));

        ConfigurationManager configManager = ConfigurationManager.createInstance(configDirectory);

        configManager.addObject(new LocaleConfig());
        configManager.addObject(new PluginConfig());

        loadLibsAndToolPaths();

        if (!loadAll && !Configuration.class.getClassLoader().getClass().getName()
                .equals(CustomURLClassLoader.class.getName())) {
            // we still are in the application first classLoader, no need to load other
            // configurables
            configManager.loadConfigs();
            return;
        }

        configManager.addObject(new LocalConfig());
        configManager.addObject(new IPEDConfig());
        configManager.addObject(new OCRConfig());
        configManager.addObject(new AdvancedIPEDConfig());
        configManager.addObject(new PDFToImageConfig());
        configManager.addObject(new SleuthKitConfig());
        configManager.addObject(new UFEDReaderConfig());
        configManager.addObject(new HashTaskConfig());
        configManager.addObject(new AudioTranscriptConfig());
        configManager.addObject(new CategoryConfig());
        configManager.addObject(new CategoryToExpandConfig());
        configManager.addObject(new CategoryToExportConfig());
        configManager.addObject(new DocThumbTaskConfig());
        configManager.addObject(new ElasticSearchTaskConfig());
        configManager.addObject(new HtmlReportTaskConfig());
        configManager.addObject(new ImageThumbTaskConfig());

        // adiciona os jars dos plugins como fonte para busca de arquivos de
        // configuração
        if (optionalJarDir != null) {
            File[] jars = optionalJarDir.listFiles();
            if (jars != null) {
                for (File jar : jars) {
                    if (jar.getName().endsWith(".jar")) {
                        try {
                            configDirectory.addZip(jar.toPath());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    if (jar.isDirectory()) {
                        configDirectory.addPath(jar.toPath());
                    }
                }
            }
        }

        configManager.loadConfigs();
    }

}
