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
package iped.app.processing;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.app.bootstrap.Bootstrap;
import iped.app.config.LogConfiguration;
import iped.app.processing.ui.ProgressConsole;
import iped.app.processing.ui.ProgressFrame;
import iped.app.ui.App;
import iped.app.ui.splash.StartUpControlClient;
import iped.app.ui.utils.UiScale;
import iped.engine.Version;
import iped.engine.config.Configuration;
import iped.engine.core.Manager;
import iped.engine.localization.Messages;
import iped.engine.preview.PreviewRepositoryManager;
import iped.engine.util.UIPropertyListenerProvider;
import iped.exception.IPEDException;
import iped.io.URLUtil;
import iped.parsers.ocr.OCRParser;

/**
 * Processing program entry point.
 */
public class Main {

    private static Logger LOGGER = null;

    String rootPath, configPath;
    File keywords;
    List<File> dataSource;
    File output;
    boolean isReportingFromCaseDir = false;
    File logFile;
    LogConfiguration logConfiguration;
    CmdLineArgsImpl cmdLineParams;

    private StartUpControlClient startUpControlClient;

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

    public String getRootPath() {
        return this.rootPath;
    }

    public File getLogFile() {
        return this.logFile;
    }

    public void setLogFile(File logFile) {
        this.logFile = logFile;
    }

    public void setLogConfiguration(LogConfiguration logConfig) {
        this.logConfiguration = logConfig;
    }

    public CmdLineArgsImpl getCmdLineArgs() {
        return this.cmdLineParams;
    }

    public String getConfigPath() {
        return this.configPath;
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
    public Main(String[] args, boolean decodeArgs) {
        lastInstance = this;
        cmdLineParams = new CmdLineArgsImpl();
        if (decodeArgs) {
            cmdLineParams.takeArgs(args);
        }
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
    public void setConfigPath() throws Exception {
        URL url = URLUtil.getURL(Main.class);

        if ("true".equals(System.getProperty("Debugging"))) {
            rootPath = System.getProperty("user.dir");
        } else {
            rootPath = new File(url.toURI()).getParent();
            // test for report generation from case folder
            if (rootPath.endsWith("iped" + File.separator + "lib")) { //$NON-NLS-1$ //$NON-NLS-2$
                isReportingFromCaseDir = true;
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

            UIPropertyListenerProvider.getInstance().firePropertyChange("mensagem", "", Messages.getString("Main.Finished")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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

            if (!cmdLineParams.isNogui() && !GraphicsEnvironment.isHeadless()) {

                // The case is still open after closing processing, so reconfigure
                // PreviewRepository readOnly
                if (App.get().appCase != null) {
                    try {
                        PreviewRepositoryManager.configureReadOnly(output.getCanonicalFile());
                    } catch (IOException e) {
                        LOGGER.error("PreviewRepository error. Close the case and open it again.", e);
                    }
                }
            }
        }
    }

    /**
     * Instancia listener de progresso, executa o processamento e aguarda.
     */
    public boolean execute() {

        // Set the UiScale (must be before any UI-related code).
        UiScale.loadUserSetting();

        UIPropertyListenerProvider provider = UIPropertyListenerProvider.getInstance();
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

        if (startUpControlClient != null) {
            startUpControlClient.finish();
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                provider.cancel(true);
            }
        });

        interruptIfBootstrapDied(System.in, provider);

        try {
            startManager();

        } finally {
            if (frame != null) {
                closeFrameinEDT(frame);
            }
        }

        return success;
    }

    private static void interruptIfBootstrapDied(InputStream is, UIPropertyListenerProvider provider) {
        Thread t = new Thread() {
            public void run() {
                byte[] buf = new byte[4096];
                try {
                    while (is.read(buf) != -1)
                        ;
                    provider.cancel(true);

                } catch (Exception e) {
                    // ignore
                }
            }
        };
        t.setDaemon(true);
        t.start();
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

        Main iped = new Main(args, true);
        PrintStream SystemOut = System.out; // this is redirected by LogConfiguration
        boolean success = false;

        iped.startUpControlClient = new StartUpControlClient();
        iped.startUpControlClient.start();

        try {
            iped.setConfigPath();
            iped.logConfiguration = new LogConfiguration(iped, null);
            iped.logConfiguration.configureLogParameters(iped.cmdLineParams.isNologfile());

            LOGGER = LoggerFactory.getLogger(Main.class);
            LOGGER.info(Version.APP_NAME);

            if (iped.isReportingFromCaseDir) {
                Configuration.getInstance().loadIpedRoot();
            } else {
                Configuration.getInstance().saveIpedRoot(iped.rootPath);
            }

            Configuration.getInstance().loadConfigurables(iped.configPath, true);
            
            SystemOut.println(Bootstrap.SUB_PROCESS_TEMP_FOLDER + System.getProperty("java.io.tmpdir"));

            success = iped.execute();

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
