package iped.app.bootstrap;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.utils.SystemUtils;

import ag.ion.bion.officelayer.application.IOfficeApplication;
import iped.app.config.LogConfiguration;
import iped.app.processing.Main;
import iped.app.ui.splash.SplashScreenManager;
import iped.app.ui.splash.StartUpControl;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.PluginConfig;
import iped.engine.util.Util;
import iped.utils.IOUtil;
import iped.viewers.util.LibreOfficeFinder;
import iped.viewers.util.UNOLibFinder;

/**
 * Bootstrap class to start the main application process with a custom classpath
 * with plugin jars.
 * 
 * @author Luís Nassif
 *
 */
public class Bootstrap {

    public static final String UI_REPORT_SYS_PROP = "iped.ui.report";
    public static final String SUB_PROCESS_TEMP_FOLDER = "IpedSubProcessTempFolder: ";

    private static String separator = SystemUtils.IS_OS_WINDOWS ? ";" : ":";

    private static volatile File subProcessTempFolder;

    public static void main(String args[]) {
        new Bootstrap().run(args);
    }

    protected boolean isToDecodeArgs() {
        return true;
    }

    protected String getDefaultClassPath(Main iped) {
        if (System.getProperty(UI_REPORT_SYS_PROP) == null)
            return iped.getRootPath() + "/iped.jar";
        else
            return iped.getRootPath() + "/lib/iped-search-app.jar";
    }

    protected String getMainClassName() {
        return Main.class.getCanonicalName();
    }

    protected float getRAMToHeapFactor() {
        return 0.25f;
    }

    protected void run(String args[]) {

        List<String> heapArgs = new ArrayList<>();
        List<String> finalArgs = new ArrayList<>();
        boolean XmxDefined = false;
        long physicalMemory = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalPhysicalMemorySize();
        for (String arg : args) {
            if (arg.startsWith("-Xms") || arg.startsWith("-Xmx")) {
                if (arg.charAt(3) == 'x') {
                    XmxDefined = true;
                }
                StringBuffer argStr = new StringBuffer();
                int i = 4;
                for (; i < arg.length(); i++) {
                    if (Character.isDigit(arg.charAt(i))) {
                        argStr.append(arg.charAt(i));
                    } else {
                        break;
                    }
                }
                long parmSize = Integer.parseInt(argStr.toString());
                switch (Character.toUpperCase(arg.charAt(i))) {
                    case 'T':
                        parmSize *= 1024;
                    case 'G':
                        parmSize *= 1024;
                    case 'M':
                        parmSize *= 1024;
                    case 'K':
                        parmSize *= 1024;
                        break;
                    default:
                        break;
                }
                long memSize = parmSize;
                if (memSize > physicalMemory) {
                    memSize = physicalMemory;
                    System.out.println("-Xms/-Xmx parameter value greater than physical memory. It was adjusted to the physical memory: " + memSize / (1024 * 1024) + "M");
                }
                heapArgs.add(arg.substring(0, 4) + memSize / (1024 * 1024) + "M");
            } else {
                finalArgs.add(arg);
            }
        }

        if (!XmxDefined) {
            // if -Xmx is not specified, set it, up to 32500MB
            long memSize = Math.min((long) (physicalMemory * getRAMToHeapFactor()), 32500L * 1024 * 1024);
            heapArgs.add("-Xmx" + (memSize / (1024 * 1024)) + "M");
        }

        Main iped = new Main(finalArgs.toArray(new String[0]), isToDecodeArgs());
        int exit = -1;
        try {
            iped.setConfigPath();
            LogConfiguration logConfig = new LogConfiguration(iped, null);
            logConfig.configureLogParameters(true);
            iped.setLogConfiguration(logConfig);

            Configuration.getInstance().loadConfigurables(iped.getConfigPath(), false);
            
            configLoaded();
            
            String classpath = getDefaultClassPath(iped);
            
            PluginConfig pluginConfig = ConfigurationManager.get().findObject(PluginConfig.class);
            
            if (pluginConfig.getTskJarFile() != null) {
                classpath += separator + pluginConfig.getTskJarFile().getAbsolutePath();
            }
            if (pluginConfig.getPluginFolder() != null) {
                classpath += separator + pluginConfig.getPluginFolder().getAbsolutePath() + "/*";
            }

            classpath = fillClassPathWithLibreOfficeJars(iped, classpath, iped.getCmdLineArgs().isNogui());

            String javaBin = "java";
            if (SystemUtils.IS_OS_WINDOWS) {
                File javaHome = new File(System.getProperty("java.home"));
                File embeddedJRE = new File(iped.getRootPath() + "/jre");
                if (!javaHome.equals(embeddedJRE)) {
                    String warn = Util.getJavaVersionWarn();
                    if (warn != null) {
                        System.err.println(warn);
                    }
                }
                javaBin = javaHome.getPath() + "/bin/java.exe";
            }

            List<String> cmd = new ArrayList<>();
            cmd.addAll(Arrays.asList(javaBin, "-cp", classpath));
            cmd.addAll(heapArgs);
            cmd.addAll(getCurrentJVMArgs());
            cmd.addAll(getCustomJVMArgs());
            cmd.addAll(getSystemProperties());
            if (SystemUtils.IS_OS_WINDOWS) {
                cmd.add("-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT"); // fix for #1719
            }
            cmd.add("-Djava.net.useSystemProxies=true"); // fix for #1446
            cmd.add(getMainClassName());
            cmd.addAll(finalArgs);
            
            ProcessBuilder pb = new ProcessBuilder();
            // pb.directory(directory)
            pb.command(cmd);

            Process process = pb.start();
            System.setProperty(StartUpControl.ipedChildProcessPID, String.valueOf(process.pid()));

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

        } catch (IllegalArgumentException e) {
            System.err.println(e.toString());

        } catch (Throwable t) {
            t.printStackTrace();
        }

        cleanTempFolder();

        System.exit(exit);
    }
    
    private static void cleanTempFolder() {
        if (subProcessTempFolder != null && subProcessTempFolder.isDirectory()) {
            for (File file : subProcessTempFolder.listFiles()) {
                if (!file.getName().equals("index")) {
                    IOUtil.deleteDirectory(file);
                }
            }
            if (!new File(subProcessTempFolder, "index").exists()) {
                subProcessTempFolder.delete();
            }
        }
    }

    /**
     * Called when loadConfigurables is done, inside run. Allow subclasses do custom
     * actions at this execution point.
     */
    protected void configLoaded() {
        new SplashScreenManager().start();
    }
    
    private static List<String> getCustomJVMArgs(){
        return Arrays.asList("-XX:+IgnoreUnrecognizedVMOptions",
                "-XX:+HeapDumpOnOutOfMemoryError",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.math=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.nio=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED");
    }

    private static List<String> getCurrentJVMArgs() {
        RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
        List<String> args = new ArrayList<>();
        for (String arg : bean.getInputArguments()) {
            if (arg.startsWith("-Xms") || arg.startsWith("-Xmx")) {
                throw new IllegalArgumentException(
                        "Please use -Xms/-Xmx arguments after iped.jar not after java command, since processing will occur in a forked process using those params.");
            }
            if ((arg.startsWith("-Xrunjdwp") || arg.startsWith("-agentlib:jdwp")) && arg.contains("server=y")) {
                // workaround as discussed in PR #1119
                Matcher matcher = Pattern.compile("address=(\\d+)").matcher(arg);
                if (matcher.find()) {
                    String match = matcher.group(0);
                    Integer port = Integer.valueOf(matcher.group(1));
                    arg = arg.replace(match, "address=" + (++port));
                }
            }
            args.add(arg);
        }
        return args;
    }

    private static List<String> getSystemProperties() {
        List<String> props = new ArrayList<>();
        for (Entry<Object, Object> e : System.getProperties().entrySet()) {
            String key = e.getKey().toString();
            if (!key.equals("java.class.path") && !key.equals("sun.boot.library.path")) {
                props.add("-D" + key + "=" + e.getValue().toString().replace("\"", "\\\""));
            }
        }
        return props;
    }

    private static String fillClassPathWithLibreOfficeJars(Main iped, String classpath, boolean isNogui)
            throws URISyntaxException, IOException {
        System.setProperty(IOfficeApplication.NOA_NATIVE_LIB_PATH,
                new File(iped.getRootPath(), "lib/nativeview").getAbsolutePath());
        LibreOfficeFinder loFinder = new LibreOfficeFinder(new File(iped.getRootPath()));
        if (loFinder.getLOPath(isNogui) != null) {
            List<File> jars = new ArrayList<>();
            UNOLibFinder.addUNOJars(loFinder.getLOPath(isNogui), jars);
            for (File jar : jars) {
                classpath += separator + jar.getCanonicalPath();
            }
        }
        return classpath;
    }

    private static void redirectStream(InputStream is, OutputStream os) {
        Thread t = new Thread() {
            public void run() {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    PrintWriter writer = new PrintWriter(os, true);
                    String line = null;
                    while ((line = reader.readLine()) != null) {
                        if (subProcessTempFolder == null && line.startsWith(SUB_PROCESS_TEMP_FOLDER)) {
                            subProcessTempFolder = new File(line.substring(SUB_PROCESS_TEMP_FOLDER.length()));
                        } else {
                            writer.println(line);
                        }
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
