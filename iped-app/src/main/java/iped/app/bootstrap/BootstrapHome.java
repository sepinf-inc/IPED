package iped.app.bootstrap;

import iped.app.home.MainFrame;
import iped.app.home.utils.CasePathManager;
import iped.app.ui.splash.SplashScreenManager;
import iped.app.ui.splash.StartUpControl;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.PluginConfig;
import iped.engine.util.Util;
import org.apache.tika.utils.SystemUtils;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bootstrap class to start the Home Application process with a custom classpath
 * with plugin jars.
 *
 * @author Luís Nassif
 * @author Thiago S. Figueiredo
 *
 */
public class BootstrapHome {

    private static final String separator = SystemUtils.IS_OS_WINDOWS ? ";" : ":";

    public static void main(String[] args) {
        new BootstrapHome().run(args);
    }

    protected String getMainClassName() {
        return MainFrame.class.getCanonicalName();
    }

    protected void run(String[] args) {

        List<String> heapArgs = new ArrayList<>();
        List<String> finalArgs = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("-Xms") || arg.startsWith("-Xmx")) {
                heapArgs.add(arg);
            } else {
                finalArgs.add(arg);
            }
        }

        int exit = -1;
        try {

            String rootPath = CasePathManager.getInstance().getConfigPath().getAbsolutePath();
            System.out.println("Root path: " + rootPath);
            System.setProperty("user.dir", CasePathManager.getInstance().getCasePath().toString());

            Configuration.getInstance().loadConfigurables(rootPath, false);
            configLoaded();

            String classpath = Paths.get(rootPath, "viped.jar").toString();


            PluginConfig pluginConfig = ConfigurationManager.get().findObject(PluginConfig.class);

            if (pluginConfig.getTskJarFile() != null) {
                classpath += separator + pluginConfig.getTskJarFile().getAbsolutePath();
            }
            if (pluginConfig.getPluginFolder() != null) {
                classpath += separator + pluginConfig.getPluginFolder().getAbsolutePath() + "/*";
            }

            String javaBin = "java";
            if (SystemUtils.IS_OS_WINDOWS) {
                File javaHome = new File(System.getProperty("java.home"));
                File embeddedJRE = Paths.get(rootPath, "jre").toFile();
                if (!javaHome.equals(embeddedJRE)) {
                    String warn = Util.getJavaVersionWarn();
                    if (warn != null) {
                        System.err.println(warn);
                    }
                }
                javaBin = Paths.get(javaHome.getPath(), "bin", "java.exe").toString();
            }

            List<String> cmd = new ArrayList<>();
            cmd.addAll(Arrays.asList(javaBin, "-cp", classpath));
            cmd.addAll(heapArgs);
            cmd.addAll(getCurrentJVMArgs());
            cmd.addAll(getCustomJVMArgs());
            cmd.addAll(getSystemProperties());
            cmd.add("-Djava.net.useSystemProxies=true"); // fix for #1446
            cmd.add(getMainClassName());
            cmd.addAll(finalArgs);

            ProcessBuilder pb = new ProcessBuilder();
            pb.command(cmd);

            Process process = pb.start();
            System.setProperty(StartUpControl.ipedChildProcessPID, String.valueOf(process.pid()));

            redirectStream(process.getInputStream(), System.out);
            redirectStream(process.getErrorStream(), System.err);
            redirectStream(System.in, process.getOutputStream());

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {}));
            exit = process.waitFor();

        } catch (IllegalArgumentException e) {
            System.err.println(e);

        } catch (Throwable t) {
            t.printStackTrace();
        }

        System.exit(exit);
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
            if (arg.startsWith("-Xrunjdwp")) {
                // workaround as discussed in PR #1119
                Matcher matcher = Pattern.compile("address=(\\d+)").matcher(arg);
                if (matcher.find()) {
                    String match = matcher.group(0);
                    int port = Integer.parseInt(matcher.group(1));
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

    private static void redirectStream(InputStream is, OutputStream os) {
        Thread t = new Thread(() -> {
                int i;
                byte[] buf = new byte[4096];
                try {
                    while ((i = is.read(buf)) != -1) {
                        os.write(buf, 0, i);
                    }
                } catch (Exception ignored) {}
        });
        t.setDaemon(true);
        t.start();
    }

}
