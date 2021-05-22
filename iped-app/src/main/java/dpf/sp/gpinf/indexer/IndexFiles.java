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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.Messages;
import dpf.sp.gpinf.indexer.Versao;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.PluginConfig;
import ag.ion.bion.officelayer.application.IOfficeApplication;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.process.Manager;
import dpf.sp.gpinf.indexer.process.ProgressConsole;
import dpf.sp.gpinf.indexer.process.ProgressFrame;
import dpf.sp.gpinf.indexer.util.CustomLoader;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.LibreOfficeFinder;
import dpf.sp.gpinf.indexer.util.UNOLibFinder;

/**
 * Ponto de entrada do programa ao processar evidências. Nome IndexFiles mantém
 * compatibilidade com o AsAP. TODO Manter apenas métodos utilizados pelo AsAP e
 * separar demais funções em outra classe de entrada com nome mais intuitivo
 * para execuções via linha de comando.
 */
public class IndexFiles {

    private static Logger LOGGER = null;

    String rootPath, configPath;
    String profile;
    File palavrasChave;
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
    private static IndexFiles lastInstance;

    /**
     * Construtor utilizado pelo AsAP
     */
    public IndexFiles(List<File> reports, File output, String configPath, File logFile, File keywordList) {
        this(reports, output, configPath, logFile, keywordList, null, null);
    }

    /**
     * Construtor utilizado pelo AsAP
     */
    public IndexFiles(List<File> reports, File output, String configPath, File logFile, File keywordList,
            List<String> bookmarksToOCR) {
        this(reports, output, configPath, logFile, keywordList, null, bookmarksToOCR);
    }

    /**
     * Construtor utilizado pelo AsAP
     */
    public IndexFiles(List<File> reports, File output, String configPath, File logFile, File keywordList,
            Boolean ignore, List<String> bookmarksToOCR) {
        lastInstance = this;
        this.dataSource = reports;
        this.output = output;
        this.palavrasChave = keywordList;
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
    public IndexFiles(String[] args) {
        lastInstance = this;
        cmdLineParams = new CmdLineArgsImpl();
        cmdLineParams.takeArgs(args);
    }

    /**
     * Obtém a última instância criada
     */
    public static IndexFiles getInstance() {
        return lastInstance;
    }

    /**
     * Define o caminho onde será encontrado o arquivo de configuração principal.
     */
    private void setConfigPath() throws Exception {
        URL url = IndexFiles.class.getProtectionDomain().getCodeSource().getLocation();

        boolean isReportFromCaseFolder = false;

        if ("true".equals(System.getProperty("Debugging"))) {
            rootPath = System.getProperty("user.dir");
        } else {
            rootPath = new File(url.toURI()).getParent();
            // test for report generation from case folder
            if (rootPath.endsWith("indexador" + File.separator + "lib")) { //$NON-NLS-1$ //$NON-NLS-2$
                rootPath = new File(url.toURI()).getParentFile().getParent();
                isReportFromCaseFolder = true;
            }
        }

        configPath = rootPath;

        profile = null;

        if (cmdLineParams.getProfile() != null) {
            profile = cmdLineParams.getProfile();
        } else if (!isReportFromCaseFolder) {
            profile = "default"; //$NON-NLS-1$
        }
        if (profile != null)
            configPath = new File(configPath, "profiles/" + profile).getAbsolutePath(); //$NON-NLS-1$ //$NON-NLS-2$

        if (!new File(configPath).exists())
            throw new IPEDException("Profile not found " + configPath); //$NON-NLS-1$
    }

    protected void startManager() {
        try {
            manager = new Manager(dataSource, output, palavrasChave);
            cmdLineParams.saveIntoCaseData(manager.getCaseData());
            manager.process();

            WorkerProvider.getInstance().firePropertyChange("mensagem", "", Messages.getString("IndexFiles.Finished")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            LOGGER.info("{} finished.", Versao.APP_EXT); //$NON-NLS-1$
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

        WorkerProvider provider = WorkerProvider.getInstance();
        provider.setExecutorThread(Thread.currentThread());

        Object frame = null;

        if (!cmdLineParams.isNogui()) {
            ProgressFrame progressFrame = new ProgressFrame(provider);
            progressFrame.setVisible(true);
            provider.addPropertyChangeListener(progressFrame);
            frame = progressFrame;
        } else {
            ProgressConsole console = new ProgressConsole();
            provider.addPropertyChangeListener(console);
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

        IndexFiles indexador = new IndexFiles(args);
        PrintStream SystemOut = System.out;
        boolean success = false;

        try {
            indexador.setConfigPath();
            indexador.logConfiguration = new LogConfiguration(indexador, logPath);
            indexador.logConfiguration.configureLogParameters(indexador.cmdLineParams.isNologfile(), fromCustomLoader);

            LOGGER = LoggerFactory.getLogger(IndexFiles.class);
            if (!fromCustomLoader)
                LOGGER.info(Versao.APP_NAME);

            Configuration.getInstance().loadConfigurables(indexador.configPath);

            if (!fromCustomLoader) {
                List<File> jars = new ArrayList<File>();
                PluginConfig pluginConfig = ConfigurationManager.findObject(PluginConfig.class);
                jars.addAll(Arrays.asList(pluginConfig.getOptionalJars(Configuration.getInstance().appRoot)));
                jars.add(Configuration.getInstance().tskJarFile);

                // currently with --nogui, user can not open analysis app, so no need to load
                // libreoffice jars
                if (!indexador.cmdLineParams.isNogui()) {
                    System.setProperty(IOfficeApplication.NOA_NATIVE_LIB_PATH,
                            new File(indexador.rootPath, "lib/nativeview").getAbsolutePath());
                    LibreOfficeFinder loFinder = new LibreOfficeFinder(new File(indexador.rootPath));
                    if (loFinder.getLOPath() != null)
                        UNOLibFinder.addUNOJars(loFinder.getLOPath(), jars);
                }

                String[] customArgs = CustomLoader.getCustomLoaderArgs(IndexFiles.class.getName(), args,
                        indexador.logFile);
                CustomLoader.run(customArgs, jars);
                return;

            } else {
                success = indexador.execute();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!success) {
            SystemOut.println("\nERROR!!!"); //$NON-NLS-1$
        } else {
            SystemOut.println("\n" + Versao.APP_EXT + " finished."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        if (indexador.logFile != null) {
            SystemOut.println("Check the log at " + indexador.logFile.getAbsolutePath()); //$NON-NLS-1$
        }

        if (getInstance().manager == null || !getInstance().manager.isSearchAppOpen())
            System.exit((success) ? 0 : 1);

        // PARA ASAP:
        // IndexFiles indexador = new IndexFiles(List<File> reports, File
        // output, String configPath, File logFile, File keywordList);
        // keywordList e logFile podem ser null. Nesse caso, o último é criado
        // na pasta log dentro de configPath
        // boolean success = indexador.executar();
    }

}
