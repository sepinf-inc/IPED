package dpf.sp.gpinf.indexer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.slf4j.bridge.SLF4JBridgeHandler;

import dpf.sp.gpinf.indexer.util.FilterOutputStream;

public class LogConfiguration {

    File logFile;
    String configPath;
    private PrintStream log, out, err;
    private SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss"); //$NON-NLS-1$

    public LogConfiguration(String configPath, File log) {
        this.configPath = configPath;
        logFile = log;
    }

    public LogConfiguration(IndexFiles indexador, String logPath) {
        configPath = indexador.configPath;
        if (logPath != null) {
            logFile = new File(logPath);
        } else {
            logFile = indexador.logFile;
            if (logFile == null)
                logFile = new File(indexador.rootPath, "log/IPED-" + df.format(new Date()) + ".log"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        indexador.logFile = logFile;
    }

    private boolean setConsoleLogFile(boolean createLogInTemp) {
        try {
            if (createLogInTemp)
                logFile = File.createTempFile("IPED", ".log"); //$NON-NLS-1$ //$NON-NLS-2$

            logFile.getParentFile().mkdirs();
            out = System.out;
            err = System.err;
            FileOutputStream fos = new FileOutputStream(logFile, true);
            log = new PrintStream(new FilterOutputStream(fos, out, "IPED")); //$NON-NLS-1$
            System.setOut(log);
            System.setErr(log);

            return true;

        } catch (IOException e) {
            if (createLogInTemp)
                e.printStackTrace();
        }
        return false;

    }

    /**
     * Fecha o arquivo de log, realizando flush automaticamente.
     */
    public void closeConsoleLogFile() {
        if (log != null) {
            log.close();
        }
        if (out != null) {
            System.setOut(out);
        }
        if (err != null) {
            System.setErr(err);
        }
    }

    public void configureLogParameters(boolean noLog, boolean finalClassLoader) throws MalformedURLException {

        System.setProperty("logFileDate", df.format(new Date())); //$NON-NLS-1$
        File configFile = null;
        if (noLog)
            configFile = new File(configPath, "conf/Log4j2ConfigurationConsoleOnly.xml"); //$NON-NLS-1$
        else {
            configFile = new File(configPath, "conf/Log4j2ConfigurationFile.xml"); //$NON-NLS-1$
            System.setProperty("logFileNamePath", logFile.getPath()); //$NON-NLS-1$
        }

        // instala bridge para capturar logs gerados pelo java.util.logging
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        // instancia o logger
        if (configFile.exists()) {
            System.setProperty("log4j.configurationFile", configFile.toURI().toURL().toString()); //$NON-NLS-1$
            LogManager.getRootLogger();
        } else
            System.out.println(
                    df.format(new Date()) + " Log4j2 configuration file not found: " + configFile.getAbsolutePath()); //$NON-NLS-1$

        if (!noLog && finalClassLoader)
            if (!setConsoleLogFile(false))
                setConsoleLogFile(true);
    }

    public PrintStream getSystemOut() {
        return out;
    }
}
