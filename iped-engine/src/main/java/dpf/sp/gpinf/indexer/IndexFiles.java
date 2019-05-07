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
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.PluginConfig;
import ag.ion.bion.officelayer.application.IOfficeApplication;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.process.Manager;
import dpf.sp.gpinf.indexer.process.ProgressConsole;
import dpf.sp.gpinf.indexer.process.ProgressFrame;
import dpf.sp.gpinf.indexer.process.task.KFFTask;
import dpf.sp.gpinf.indexer.util.CustomLoader;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.LibreOfficeFinder;
import dpf.sp.gpinf.indexer.util.UNOLibFinder;
import dpf.sp.gpinf.indexer.util.UTF8Properties;

/**
 * Ponto de entrada do programa ao processar evidências. Nome IndexFiles mantém
 * compatibilidade com o AsAP. TODO Manter apenas métodos utilizados pelo AsAP e
 * separar demais funções em outra classe de entrada com nome mais intuitivo
 * para execuções via linha de comando.
 */
public class IndexFiles extends SwingWorker<Boolean, Integer> {

    private static Logger LOGGER = null;
    /**
     * command line parameters
     */
    public boolean fromCmdLine = false;
    public boolean appendIndex = false;

    String rootPath, configPath;
    boolean nogui = false;
    boolean nologfile = false;
    File palavrasChave;
    List<File> dataSource;
    File output;

    File logFile;
    LogConfiguration logConfiguration;

    private CmdLineArgs cmdLineParams;

    private ProgressFrame progressFrame;

    private Manager manager;

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
        super();
        lastInstance = this;
        this.dataSource = reports;
        this.output = output;
        this.palavrasChave = keywordList;
        this.configPath = configPath;
        this.logFile = logFile;
        OCRParser.bookmarksToOCR = bookmarksToOCR;
    }

    /**
     * Contrutor utilizado pela execução via linha de comando
     */
    public IndexFiles(String[] args) {
        super();
        lastInstance = this;
        cmdLineParams = new CmdLineArgs();
        cmdLineParams.takeArgs(args);
        this.fromCmdLine = true;
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

        if ("true".equals(System.getProperty("Debugging"))) {
            rootPath = System.getProperty("user.dir");
        } else {
            rootPath = new File(url.toURI()).getParent();
            // test for report generation from case folder
            if (rootPath.endsWith("indexador" + File.separator + "lib")) //$NON-NLS-1$ //$NON-NLS-2$
                rootPath = new File(url.toURI()).getParentFile().getParent();
        }

        configPath = rootPath;
        String locale = getProfileLocale();

        String profile = null;

        if (cmdLineParams.getProfile() != null) {
            profile = cmdLineParams.getProfile();
        } else if (!locale.equals("pt-BR")) //$NON-NLS-1$
            profile = "default"; //$NON-NLS-1$

        if (profile != null)
            configPath = new File(configPath, "profiles/" + locale + "/" + profile).getAbsolutePath(); //$NON-NLS-1$ //$NON-NLS-2$

        if (!new File(configPath).exists())
            throw new IPEDException("Profile not found " + configPath); //$NON-NLS-1$
    }

    public String getProfileLocale() throws IOException {
        UTF8Properties props = new UTF8Properties();
        props.load(new File(rootPath, Configuration.LOCAL_CONFIG));
        String locale = props.getProperty("locale").trim(); //$NON-NLS-1$
        return locale;
    }

    /**
     * Importa base de hashes no formato NSRL.
     *
     * @param kffPath
     *            caminho para base de hashes.
     */
    void importKFF(File kffPath) {
        try {
            setConfigPath();
            Configuration.getInstance().getConfiguration(configPath);
            KFFTask kff = new KFFTask();
            kff.init(Configuration.getInstance().properties, null);
            kff.importKFF(kffPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Realiza o processamento numa worker thread.
     *
     * @see javax.swing.SwingWorker#doInBackground()
     */
    @Override
    protected Boolean doInBackground() {
        try {
            manager = new Manager(dataSource, output, palavrasChave);
            cmdLineParams.saveIntoCaseData(manager.getCaseData());
            manager.process();

            this.firePropertyChange("mensagem", "", Messages.getString("IndexFiles.Finished")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            LOGGER.info("{} finished.", Versao.APP_EXT); //$NON-NLS-1$
            success = true;

        } catch (Throwable e) {
            success = false;
            if (e instanceof IPEDException)
                LOGGER.error("Processing Error: " + e.getMessage()); //$NON-NLS-1$
            else
                LOGGER.error("Processing Error: ", e); //$NON-NLS-1$

        } finally {
            done = true;
            if (manager != null)
                manager.setProcessingFinished(true);
            if (manager == null || !manager.isSearchAppOpen())
                logConfiguration.closeConsoleLogFile();
        }

        return success;
    }

    /**
     * Chamado após processamento para liberar recursos.
     *
     * @see javax.swing.SwingWorker#done()
     */
    @Override
    public void done() {
        if (progressFrame != null) {
            progressFrame.dispose();
        }
    }

    volatile boolean done = false, success = false;

    /**
     * Instancia listener de progresso, executa o processamento e aguarda.
     */
    public boolean executar() {
        if (!nogui) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    progressFrame = new ProgressFrame(lastInstance);
                    lastInstance.addPropertyChangeListener(progressFrame);
                    progressFrame.setVisible(true);
                }
            });
        } else {
            ProgressConsole console = new ProgressConsole();
            this.addPropertyChangeListener(console);
        }

        this.execute();

        while (!done) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
        }

        return success;
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
            indexador.logConfiguration.configureLogParameters(indexador.nologfile, fromCustomLoader);

            LOGGER = LoggerFactory.getLogger(IndexFiles.class);
            if (!fromCustomLoader)
                LOGGER.info(Versao.APP_NAME);

            Configuration.getInstance().loadConfigurables(indexador.configPath);

            if (!fromCustomLoader) {
                List<File> jars = new ArrayList<File>();
                PluginConfig pluginConfig = (PluginConfig) ConfigurationManager.getInstance()
                        .findObjects(PluginConfig.class).iterator().next();
                jars.addAll(Arrays.asList(pluginConfig.getOptionalJars(Configuration.getInstance().appRoot)));
                jars.add(Configuration.getInstance().tskJarFile);

                // currently with --nogui, user can not open analysis app, so no need to load
                // libreoffice jars
                if (!indexador.nogui) {
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
                success = indexador.executar();
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
