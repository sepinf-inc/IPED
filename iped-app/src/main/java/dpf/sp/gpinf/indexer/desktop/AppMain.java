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

import ag.ion.bion.officelayer.application.IOfficeApplication;
import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.LogConfiguration;
import dpf.sp.gpinf.indexer.Versao;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.PluginConfig;
import dpf.sp.gpinf.indexer.process.Manager;
import dpf.sp.gpinf.indexer.util.CustomLoader;
import dpf.sp.gpinf.indexer.util.LibreOfficeFinder;
import dpf.sp.gpinf.indexer.util.UNOLibFinder;
import dpf.sp.gpinf.indexer.util.Util;

public class AppMain {

    private static final String appLogFileName = "IPED-SearchApp.log"; //$NON-NLS-1$

    File casePath;
    File testPath;// = new File("E:\\teste\\noteAcer-forensic-3.15-2");

    boolean isMultiCase = false;
    boolean nolog = false;
    File casesPathFile = null;
    File libDir;

    public static void main(String[] args) {
        checkJavaVersion();
        AppMain appMain = new AppMain();
        appMain.detectCasePath();
        appMain.start(args);
    }

    private static void checkJavaVersion() {
        try {
            if (System.getProperty("iped.javaVersionChecked") != null) //$NON-NLS-1$
                return;

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    String warn = Util.getJavaVersionWarn();
                    if (warn != null) {
                        JOptionPane.showMessageDialog(null, warn, Messages.getString("AppMain.warn.Title"), //$NON-NLS-1$
                                JOptionPane.WARNING_MESSAGE);
                    }
                    Messages.resetLocale();
                }
            });
            System.setProperty("iped.javaVersionChecked", "true"); //$NON-NLS-1$

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void start(String[] args) {
        start(casePath, null, args);
    }

    private void detectCasePath() {
        if (testPath != null) {
            casePath = testPath;
            return;
        }

        if ("true".equals(System.getProperty("Debugging"))) {
            casePath = new File(System.getProperty("user.dir"));
            return;
        }

        libDir = detectLibDir();
        casePath = libDir.getParentFile().getParentFile();

        if (!new File(casePath, "indexador").exists()) //$NON-NLS-1$
            casePath = null;
    }

    private File detectLibDir() {
        URL url = AppMain.class.getProtectionDomain().getCodeSource().getLocation();
        File jarFile = null;
        try {
            if (url.toURI().getAuthority() == null)
                jarFile = new File(url.toURI());
            else
                jarFile = new File(url.toURI().getSchemeSpecificPart());

            return jarFile.getParentFile();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void loadArgs(String[] args) {
        if (args == null)
            return;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--nologfile")) //$NON-NLS-1$
                nolog = true;
            if (args[i].equals("-multicases")) { //$NON-NLS-1$
                isMultiCase = true;
                casesPathFile = new File(args[i + 1]).getAbsoluteFile();

                if (!casesPathFile.exists()) {
                    System.out.println(Messages.getString("AppMain.NoCasesFile") + args[1]); //$NON-NLS-1$
                    System.exit(1);
                }
            }
        }
    }

    public void start(File casePath, Manager processingManager, String[] args) {

        try {
            boolean fromCustomLoader = CustomLoader.isFromCustomLoader(args);
            if (fromCustomLoader)
                args = CustomLoader.clearCustomLoaderArgs(args);

            boolean finalLoader = testPath != null || fromCustomLoader;

            loadArgs(args);

            if (casesPathFile == null)
                casesPathFile = casePath;

            File logParent = casesPathFile;
            if (isMultiCase && casesPathFile.isFile())
                logParent = casesPathFile.getParentFile();

            File logFile = new File(logParent, appLogFileName).getCanonicalFile();
            LogConfiguration logConfiguration = null;

            if (libDir == null)
                libDir = detectLibDir();

            if (processingManager == null) {
                logConfiguration = new LogConfiguration(libDir.getParentFile().getAbsolutePath(), logFile);
                logConfiguration.configureLogParameters(nolog, finalLoader);

                Logger LOGGER = LoggerFactory.getLogger(AppMain.class);
                if (!fromCustomLoader)
                    LOGGER.info(Versao.APP_NAME);
            }

            Configuration.getInstance().loadConfigurables(libDir.getParentFile().getAbsolutePath());

            if (!finalLoader && processingManager == null) {
                List<File> jars = new ArrayList<File>();
                PluginConfig pluginConfig = (PluginConfig) ConfigurationManager.getInstance()
                        .findObjects(PluginConfig.class).iterator().next();
                jars.addAll(Arrays.asList(pluginConfig.getOptionalJars(Configuration.getInstance().appRoot)));
                jars.add(Configuration.getInstance().tskJarFile);

                System.setProperty(IOfficeApplication.NOA_NATIVE_LIB_PATH,
                        new File(libDir, "nativeview").getAbsolutePath());
                LibreOfficeFinder loFinder = new LibreOfficeFinder(libDir.getParentFile());
                if (loFinder.getLOPath() != null)
                    UNOLibFinder.addUNOJars(loFinder.getLOPath(), jars);

                String[] customArgs = CustomLoader.getCustomLoaderArgs(this.getClass().getName(), args, logFile);

                CustomLoader.run(customArgs, jars);

            } else {
                App.get().getSearchParams().codePath = libDir.getAbsolutePath();
                App.get().init(logConfiguration, isMultiCase, casesPathFile, processingManager);

                InicializarBusca init = new InicializarBusca(processingManager);
                init.execute();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
