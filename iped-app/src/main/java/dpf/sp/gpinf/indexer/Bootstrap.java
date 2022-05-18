package dpf.sp.gpinf.indexer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.tika.utils.SystemUtils;

import ag.ion.bion.officelayer.application.IOfficeApplication;
import dpf.sp.gpinf.indexer.config.Configuration;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.PluginConfig;
import dpf.sp.gpinf.indexer.util.LibreOfficeFinder;
import dpf.sp.gpinf.indexer.util.UNOLibFinder;

/**
 * Bootstrap class to start the main application process with a custom classpath
 * with plugin jars. TODO: copy System properties and JVM params to forked
 * process.
 * 
 * @author Luís Nassif
 *
 */
public class Bootstrap {

    private static String XMX = "24G"; // TODO read from cmd line

    private static String separator = SystemUtils.IS_OS_WINDOWS ? ";" : ":";

    public static void main(String args[]) {
        new Bootstrap().run(args);
    }

    protected boolean isToDecodeArgs() {
        return true;
    }

    protected String getDefaultClassPath(Main iped) {
        return iped.rootPath + "/iped.jar";
    }

    protected String getMainClassName() {
        return Main.class.getCanonicalName();
    }

    protected void run(String args[]) {

        Main iped = new Main(args, isToDecodeArgs());
        int exit = -1;
        try {
            iped.setConfigPath();
            iped.logConfiguration = new LogConfiguration(iped, null);
            iped.logConfiguration.configureLogParameters(true, true);

            Configuration.getInstance().loadConfigurables(iped.configPath, false);
            
            String classpath = getDefaultClassPath(iped);
            
            PluginConfig pluginConfig = ConfigurationManager.get().findObject(PluginConfig.class);
            
            if (pluginConfig.getTskJarFile() != null) {
                classpath += separator + pluginConfig.getTskJarFile().getAbsolutePath();
            }
            if (pluginConfig.getPluginFolder() != null) {
                classpath += separator + pluginConfig.getPluginFolder().getAbsolutePath() + "/*";
            }

            // user can't open analysis UI w/ --nogui, so no need to load libreoffice jars
            if (!iped.cmdLineParams.isNogui()) {
                classpath = fillClassPathWithLibreOfficeJars(iped, classpath);
            }

            String javaBin = SystemUtils.IS_OS_WINDOWS ? iped.rootPath + "/jre/bin/java.exe" : "java";

            List<String> cmd = new ArrayList<>();
            cmd.addAll(Arrays.asList(javaBin, "-cp", classpath, "-Xmx" + XMX));
            cmd.add(getMainClassName());
            cmd.addAll(Arrays.asList(args));
            
            ProcessBuilder pb = new ProcessBuilder();
            // pb.directory(directory)
            pb.command(cmd);

            Process process = pb.start();

            redirectStream(process.getInputStream(), System.out);
            redirectStream(process.getErrorStream(), System.err);
            redirectStream(System.in, process.getOutputStream());

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    // should we destroy the forked process, possibly forcibly?
                    // currently it closes itself if this process dies
                    // process.destroy();
                }
            });

            exit = process.waitFor();

        } catch (Throwable t) {
            t.printStackTrace();
        }

        System.exit(exit);
    }

    private static String fillClassPathWithLibreOfficeJars(Main iped, String classpath)
            throws URISyntaxException, IOException {
        System.setProperty(IOfficeApplication.NOA_NATIVE_LIB_PATH,
                new File(iped.rootPath, "lib/nativeview").getAbsolutePath());
        LibreOfficeFinder loFinder = new LibreOfficeFinder(new File(iped.rootPath));
        if (loFinder.getLOPath() != null) {
            List<File> jars = new ArrayList<>();
            UNOLibFinder.addUNOJars(loFinder.getLOPath(), jars);
            for (File jar : jars) {
                classpath += separator + jar.getCanonicalPath();
            }
        }
        return classpath;
    }

    private static void redirectStream(InputStream is, OutputStream os) {
        Thread t = new Thread() {
            public void run() {
                int i;
                byte[] buf = new byte[4096];
                try {
                    while ((i = is.read(buf)) != -1) {
                        os.write(buf, 0, i);
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }

}
