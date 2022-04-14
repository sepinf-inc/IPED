/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
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
import java.io.PrintStream;
import java.net.URL;
import java.security.Policy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.config.Configuration;
import dpf.sp.gpinf.indexer.localization.Messages;
import dpf.sp.gpinf.indexer.Version;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.PluginConfig;
import ag.ion.bion.officelayer.application.IOfficeApplication;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.process.Manager;
import dpf.sp.gpinf.indexer.process.ProgressConsole;
import dpf.sp.gpinf.indexer.process.ProgressFrame;
import dpf.sp.gpinf.indexer.ui.UiScale;
import dpf.sp.gpinf.indexer.util.CustomLoader;
import dpf.sp.gpinf.indexer.util.DefaultPolicy;
import iped3.exception.IPEDException;
import dpf.sp.gpinf.indexer.util.LibreOfficeFinder;
import dpf.sp.gpinf.indexer.util.UNOLibFinder;

/**
 * Processing program entry point.
 */
public class Main {

    private static Logger LOGGER = null;

    String rootPath, configPath;
    File keywords;
    List<File> dataSource;
    File output;

    File logFile;
    LogConfiguration logConfiguration;

    private CmdLineArgsImpl cmdLineParams;

    private Manager manager;

    volatile boolean success = false;

    /**
     * Última instância criada deta classe.
     */
    private static Main lastInstance;

    /**
     * Construtor utilizado pelo AsAP
     */
    public Main(List<File> reports, File output, String configPath, File logFile, File keywordList) {
        this(reports, output, configPath, logFile, keywordList, null, null);
    }

    /**
     * Construtor utilizado pelo AsAP
     */
    public Main(List<File> reports, File output, String configPath, File logFile, File keywordList,
            List<String> bookmarksToOCR) {
        this(reports, output, configPath, logFile, keywordList, null, bookmarksToOCR);
    }

    /**
     * Construtor utilizado pelo AsAP
     */
    public Main(List<File> reports, File output, String configPath, File logFile, File keywordList,
            Boolean ignore, List<String> bookmarksToOCR) {
        lastInstance = this;
        this.dataSource = reports;
        this.output = output;
        this.keywords = keywordList;
        this.configPath = configPath;
        this.logFile = logFile;

        String list = "";
        for (String o : bookmarksToOCR)
            list += o + OCRParser.SUBSET_SEPARATOR;
        System.setProperty(OCRParser.SUBSET_TO_OCR, list);
    }

    /**
     * Contrutor utilizado pela execução via linha de comando
     */
    public Main(String[] args) {
        lastInstance = this;
        cmdLineParams = new CmdLineArgsImpl();
        cmdLineParams.takeArgs(args);
    }

    /**
     * Obtém a última instância criada
     */
    public static Main getInstance() {
        return lastInstance;
    }

    /**
     * Define o caminho onde será encontrado o arquivo de configuração principal.
     */
    private void setConfigPath() throws Exception {
        URL url = Main.class.getProtectionDomain().getCodeSource().getLocation();

        if ("true".equals(System.getProperty("Debugging"))) {
            rootPath = System.getProperty("user.dir");
        } else {
            rootPath = new File(url.toURI()).getParent();
            // test for report generation from case folder
            if (rootPath.endsWith("iped" + File.separator + "lib")) { //$NON-NLS-1$ //$NON-NLS-2$
                rootPath = new File(url.toURI()).getParentFile().getParent();
            }
        }

        configPath = rootPath;

        String profile = cmdLineParams.getProfile();
        if (profile != null) {
            configPath = new File(configPath, Configuration.PROFILES_DIR + "/" + profile).getAbsolutePath(); //$NON-NLS-1$
        }
        if (!new File(configPath).exists())
            throw new IPEDException("Profile not found " + configPath); //$NON-NLS-1$
    }

    protected void startManager() {
        try {
            manager = new Manager(dataSource, output, keywords);
            cmdLineParams.saveIntoCaseData(manager.getCaseData());
            manager.process();

            WorkerProvider.getInstance().firePropertyChange("mensagem", "", Messages.getString("Main.Finished")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            LOGGER.info("{} finished.", Version.APP_EXT); //$NON-NLS-1$
            success = true;

        } catch (Throwable e) {
            success = false;
            if (e instanceof IPEDException)
                LOGGER.error("Processing Error: " + e.getMessage()); //$NON-NLS-1$
            else
                LOGGER.error("Processing Error: ", e); //$NON-NLS-1$

            if (e instanceof OutOfMemoryError || (e.getCause() instanceof OutOfMemoryError))
                LOGGER.error("Processing aborted because of OutOfMemoryError. See the possible workarounds at " //$NON-NLS-1$
                        + "https://github.com/sepinf-inc/IPED/wiki/Troubleshooting"); //$NON-NLS-1$

        } finally {
            if (manager != null)
                manager.setProcessingFinished(true);
            if (manager == null || !manager.isSearchAppOpen())
                logConfiguration.closeConsoleLogFile();
        }
    }

    /**
     * Instancia listener de progresso, executa o processamento e aguarda.
     */
    public boolean execute() {

        // Set the UiScale (must be before any UI-related code).
        UiScale.loadUserSetting();

        WorkerProvider provider = WorkerProvider.getInstance();
        provider.setExecutorThread(Thread.currentThread());

        Object frame = null;

        if (!cmdLineParams.isNogui()) {
            ProgressFrame progressFrame = new ProgressFrame(provider);
            progressFrame.setVisible(true);
            provider.addPropertyChangeListener(progressFrame, true);
            frame = progressFrame;
        } else {
            ProgressConsole console = new ProgressConsole();
            provider.addPropertyChangeListener(console, false);
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                provider.cancel(true);
            }
        });

        try {
            startManager();

        } finally {
            if (frame != null) {
                closeFrameinEDT(frame);
            }
        }

        return success;
    }

    private void closeFrameinEDT(Object frame) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ((ProgressFrame) frame).dispose();
            }
        });
    }

    /**
     * Entrada principal da aplicação para processamento de evidências
     */
    public static void main(String[] args) {
        boolean fromCustomLoader = CustomLoader.isFromCustomLoader(args);
        String logPath = null;
        if (fromCustomLoader) {
            logPath = CustomLoader.getLogPathFromCustomArgs(args);
            args = CustomLoader.clearCustomLoaderArgs(args);
        }

        Main iped = new Main(args);
        PrintStream SystemOut = System.out;
        boolean success = false;

        try {
            iped.setConfigPath();
            iped.logConfiguration = new LogConfiguration(iped, logPath);
            iped.logConfiguration.configureLogParameters(iped.cmdLineParams.isNologfile(), fromCustomLoader);

            LOGGER = LoggerFactory.getLogger(Main.class);
            if (!fromCustomLoader)
                LOGGER.info(Version.APP_NAME);

            Configuration.getInstance().loadConfigurables(iped.configPath);

            if (!fromCustomLoader) {

                // blocks internet access from viewers
                Policy.setPolicy(new DefaultPolicy());
                System.setSecurityManager(new SecurityManager());

                List<File> jars = new ArrayList<File>();
                PluginConfig pluginConfig = ConfigurationManager.get().findObject(PluginConfig.class);
                jars.addAll(Arrays.asList(pluginConfig.getPluginJars()));
                jars.add(pluginConfig.getTskJarFile());

                // currently with --nogui, user can not open analysis app, so no need to load
                // libreoffice jars
                if (!iped.cmdLineParams.isNogui()) {
                    System.setProperty(IOfficeApplication.NOA_NATIVE_LIB_PATH,
                            new File(iped.rootPath, "lib/nativeview").getAbsolutePath());
                    LibreOfficeFinder loFinder = new LibreOfficeFinder(new File(iped.rootPath));
                    if (loFinder.getLOPath() != null)
                        UNOLibFinder.addUNOJars(loFinder.getLOPath(), jars);
                }

                String[] customArgs = CustomLoader.getCustomLoaderArgs(Main.class.getName(), args,
                        iped.logFile);
                CustomLoader.run(customArgs, jars);
                return;

            } else {
                success = iped.execute();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!success) {
            SystemOut.println("\nERROR!!!"); //$NON-NLS-1$
        } else {
            SystemOut.println("\n" + Version.APP_EXT + " finished."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        if (iped.logFile != null) {
            SystemOut.println("Check the log at " + iped.logFile.getAbsolutePath()); //$NON-NLS-1$
        }

        if (getInstance().manager == null || !getInstance().manager.isSearchAppOpen())
            System.exit((success) ? 0 : 1);

        // PARA ASAP:
        // Main iped = new Main(List<File> reports, File
        // output, String configPath, File logFile, File keywordList);
        // keywordList e logFile podem ser null. Nesse caso, o último é criado
        // na pasta log dentro de configPath
        // boolean success = iped.executar();
    }

}
