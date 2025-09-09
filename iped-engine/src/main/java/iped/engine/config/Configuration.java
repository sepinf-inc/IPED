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
package iped.engine.config;

import java.io.File;
import java.io.IOException;
import java.net.SocketPermission;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Policy;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.NoOpLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.configuration.IConfigurationDirectory;
import iped.engine.task.AbstractTask;
import iped.engine.util.Util;
import iped.utils.UTF8Properties;
import iped.viewers.util.DefaultPolicy;

/**
 * Classe principal de carregamento e acesso às configurações da aplicação.
 */
public class Configuration {

    public static final String CONFIG_FILE = "IPEDConfig.txt"; //$NON-NLS-1$
    public static final String LOCAL_CONFIG = "LocalConfig.txt"; //$NON-NLS-1$
    public static final String CONF_DIR = "conf"; //$NON-NLS-1$
    public static final String PROFILES_DIR = "profiles"; //$NON-NLS-1$
    public static final String CASE_PROFILE_DIR = "profile"; //$NON-NLS-1$

    private static final File ipedRoot = new File(System.getProperty("user.home"), ".iped/ipedRoot.txt");

    private static Configuration singleton;
    private static AtomicBoolean loaded = new AtomicBoolean();

    private ConfigurationDirectory configDirectory;
    public Logger logger;
    public UTF8Properties properties = new UTF8Properties();
    public String configPath, appRoot;
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
        if (appRoot.contains(PROFILES_DIR))
            appRoot = new File(appRoot).getParentFile().getParent();
        return appRoot;
    }

    private void configureLogger(String configPath) {
        // DataSource.testConnection(configPathStr);
        LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", NoOpLog.class.getName()); //$NON-NLS-1$
        logger = LoggerFactory.getLogger(Configuration.class);
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

        System.setProperty(IConfigurationDirectory.IPED_APP_ROOT, appRoot);
        System.setProperty(IConfigurationDirectory.IPED_CONF_PATH, configPath);

        properties.load(new File(appRoot, LOCAL_CONFIG));
        File mainConfig = new File(configPath, CONFIG_FILE);
        if (mainConfig.exists()) {
            properties.load(mainConfig);
        }
    }

    public void saveIpedRoot(String path) throws IOException {
        System.setProperty(IConfigurationDirectory.IPED_ROOT, path);
        ipedRoot.getParentFile().mkdirs();
        Files.write(ipedRoot.toPath(), path.getBytes(StandardCharsets.UTF_8));
    }

    public void loadIpedRoot() throws IOException {
        if (ipedRoot.exists()) {
            byte[] bytes = Files.readAllBytes(ipedRoot.toPath());
            String path = new String(bytes, StandardCharsets.UTF_8);
            System.setProperty(IConfigurationDirectory.IPED_ROOT, path);
        }
    }

    public void loadNativeLibs() throws IOException {
        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) { //$NON-NLS-1$ //$NON-NLS-2$

            String arch = "x86"; //$NON-NLS-1$
            if (System.getProperty("os.arch").contains("64")) //$NON-NLS-1$ //$NON-NLS-2$
                arch = "x64"; //$NON-NLS-1$

            loaddbPathWin = appRoot + "/tools/tsk/" + arch + "/tsk_loaddb"; //$NON-NLS-1$ //$NON-NLS-2$

            File nativelibs = new File(loaddbPathWin).getParentFile().getParentFile();
            nativelibs = new File(nativelibs, arch);
            Util.loadNatLibs(nativelibs);
        }
    }

    public void loadConfigurables(String configPathStr) throws IOException {
        loadConfigurables(configPathStr, false);
    }

    private void addProfileToConfigDirectory(ConfigurationDirectory configDirectory, File profile) {
        File mainConfig = new File(profile, CONFIG_FILE);
        if (mainConfig.exists()) {
            configDirectory.addPath(mainConfig.toPath());
        }
        File localConfig = new File(profile, LOCAL_CONFIG);
        if (localConfig.exists()) {
            configDirectory.addPath(localConfig.toPath());
        }
        File configDir = new File(profile, CONF_DIR);
        if (configDir.exists()) {
            configDirectory.addPath(configDir.toPath());
        }
    }

    public void loadConfigurables(String configPathStr, boolean loadAll) throws IOException {

        if (loaded.getAndSet(true))
            return;

        getConfiguration(configPathStr);

        if (loadAll) {
            logger.info("Loading configuration from " + configPath); //$NON-NLS-1$
        }

        configDirectory = new ConfigurationDirectory(Paths.get(appRoot, LOCAL_CONFIG));

        File defaultProfile = new File(appRoot);
        File currentProfile = new File(configPathStr);
        File caseProfile = new File(appRoot, CASE_PROFILE_DIR);
        addProfileToConfigDirectory(configDirectory, defaultProfile);
        if (!currentProfile.equals(defaultProfile)) {
            addProfileToConfigDirectory(configDirectory, currentProfile);
        } else if (caseProfile.exists()) {
            addProfileToConfigDirectory(configDirectory, caseProfile);
        }

        ConfigurationManager configManager = ConfigurationManager.createInstance(configDirectory);
        LocaleConfig lc = new LocaleConfig();
        configManager.addObject(lc);
        configManager.loadConfig(lc);

        PluginConfig pluginConfig = new PluginConfig();
        configManager.addObject(pluginConfig);
        configManager.loadConfig(pluginConfig);
        addPluginJarsToConfigurationLookup(configDirectory, pluginConfig);

        configManager.addObject(new SplashScreenConfig());
        
        if (!loadAll) {
            configManager.loadConfigs();
            return;
        }

        loadNativeLibs();

        configManager.addObject(new LocalConfig());
        configManager.addObject(new OCRConfig());
        configManager.addObject(new FileSystemConfig());
        configManager.addObject(new AnalysisConfig());
        configManager.addObject(new ProcessingPriorityConfig());

        configManager.addObject(new EnableTaskProperty(FaceRecognitionConfig.enableParam));

        TaskInstallerConfig taskConfig = new TaskInstallerConfig();
        configManager.addObject(taskConfig);

        // must load taskConfig before using it
        configManager.loadConfig(taskConfig);

        for (AbstractTask task : taskConfig.getNewTaskInstances()) {
            for (Configurable<?> configurable : task.getConfigurables()) {
                configManager.addObject(configurable);
            }
        }

        configManager.loadConfigs();


        // blocks internet access from html viewers
        DefaultPolicy policy = new DefaultPolicy();
        MinIOConfig minIOConfig = configManager.findObject(MinIOConfig.class);
        if (minIOConfig.isEnabled()) {
            String host = minIOConfig.getHostAndPort();
            if (host.startsWith("http://") || host.startsWith("https://")) {
                host = host.substring(host.indexOf("://") + 3);
            }
            policy.addAllowedPermission(new SocketPermission(host, "connect,resolve"));
        }
        Policy.setPolicy(policy);
        System.setSecurityManager(new SecurityManager());
    }

    // add plugin jars to the configuration resource look up engine
    private void addPluginJarsToConfigurationLookup(ConfigurationDirectory configDirectory, PluginConfig pluginConfig) {
        File[] jars = pluginConfig.getPluginJars();
        for (File jar : jars) {
            if (jar.getName().endsWith(".jar")) {
                try {
                    configDirectory.addZip(jar.toPath());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            if (jar.isDirectory()) {
                configDirectory.addPath(jar.toPath());
            }
        }
    }

}
