package iped.engine.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;

import iped.utils.UTF8Properties;

public class LocalConfig extends AbstractPropertiesConfigurable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final String CONFIG_FILE = "LocalConfig.txt"; //$NON-NLS-1$

    public static final String SYS_PROP_APPEND = "iped.appending"; //$NON-NLS-1$

    /**
     * Temp folder created by the process that started this JVM, which also passed
     * it as java.io.tmpdir in the command line. Since Java 21 the JVM reads
     * java.io.tmpdir only at startup, so changing it with System.setProperty()
     * does not change where File.createTempFile() and Files.createTempFile()
     * create their files.
     */
    public static final String SYS_PROP_IPED_TEMP = "iped.tempdir";
    private static final String SYS_PROP_BASE_TMPDIR = "java.io.basetmpdir";

    public static final String NUM_THREADS = "numThreads";

    private static final String HASH_DB = "hashesDB";

    private static final String IPED_TEMP = "indexTemp";

    private static final String TEMP_ON_SSD = "indexTempOnSSD";

    private static final String OUTPUT_ON_SSD = "outputOnSSD";

    private static final String DEFAULT_VAL = "default";

    public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return entry.endsWith(CONFIG_FILE);
        }
    };

    private boolean indexTempOnSSD = false;
    private boolean outputOnSSD = false;
    private File ipedTemp, indexTemp;
    private int numThreads;
    private File hashDbFile;

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return filter;
    }

    @Override
    public void processProperties(UTF8Properties properties) {

        Logger logger = Configuration.getInstance().logger;

        String value;

        if (ipedTemp == null) {
            File configuredTemp = getConfiguredTemp(properties, logger);
            String presetTemp = System.getProperty(SYS_PROP_IPED_TEMP);
            if (presetTemp != null && new File(presetTemp).isDirectory()) {
                // created by Bootstrap, this JVM was started with java.io.tmpdir pointing to it
                ipedTemp = new File(presetTemp);
            } else {
                ipedTemp = createIpedTemp(configuredTemp);
            }
            if (ipedTemp.exists()) {
                // for code that reads the property, see SYS_PROP_IPED_TEMP about createTempFile()
                System.setProperty("java.io.tmpdir", ipedTemp.getAbsolutePath()); //$NON-NLS-1$
            }
            if (configuredTemp != null && configuredTemp.equals(ipedTemp.getParentFile())) {
                indexTemp = new File(ipedTemp, "index"); //$NON-NLS-1$
            }
        }
        if (ipedTemp != null) {
            ipedTemp.mkdirs();
        }

        value = properties.getProperty(NUM_THREADS); // $NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.equalsIgnoreCase(DEFAULT_VAL)) { // $NON-NLS-1$
            numThreads = Integer.valueOf(value);
        } else {
            numThreads = Runtime.getRuntime().availableProcessors();
        }

        value = properties.getProperty(TEMP_ON_SSD); // $NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            indexTempOnSSD = Boolean.valueOf(value);
        }

        value = properties.getProperty(OUTPUT_ON_SSD); // $NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (value != null && !value.isEmpty()) {
            outputOnSSD = Boolean.valueOf(value);
        }

        if (outputOnSSD || !indexTempOnSSD || Boolean.valueOf(System.getProperty(SYS_PROP_APPEND)))
            indexTemp = null;

        value = properties.getProperty(HASH_DB);
        if (value != null) {
            setHashDbFile(new File(value.trim()));
        }

    }

    /**
     * Folder set in indexTemp, created if needed, or null if it is "default" or
     * can not be created.
     */
    public static File getConfiguredTemp(UTF8Properties properties, Logger logger) {
        String value = properties.getProperty(IPED_TEMP);
        if (value == null || value.trim().equalsIgnoreCase(DEFAULT_VAL)) {
            return null;
        }
        File dir = new File(value.trim()).getAbsoluteFile();
        if (!dir.exists() && !dir.mkdirs()) {
            if (logger != null)
                logger.info("Fail to create temp directory" + dir.getAbsolutePath());
            return null;
        }
        return dir;
    }

    /**
     * Creates a new "iped-temp[random]" folder in the given folder or, if it is
     * null or the folder can not be created there, in the system temp folder.
     */
    public static File createIpedTemp(File configuredTemp) {
        if (System.getProperty(SYS_PROP_BASE_TMPDIR) == null) {
            System.setProperty(SYS_PROP_BASE_TMPDIR, System.getProperty("java.io.tmpdir"));
        }
        File systemTemp = new File(System.getProperty(SYS_PROP_BASE_TMPDIR));
        Random rand = new Random();
        File dir = new File(configuredTemp != null ? configuredTemp : systemTemp, "iped-temp" + rand.nextLong());
        if (!dir.mkdirs()) {
            dir = new File(systemTemp, "iped-temp" + rand.nextLong());
            dir.mkdirs();
        }
        return dir;
    }

    public void setIndexerTemp(File temp) {
        ipedTemp = temp;
        indexTemp = new File(ipedTemp, "index"); //$NON-NLS-1$
    }

    public File getIndexerTemp() {
        return ipedTemp;
    }

    public boolean isIndexTempOnSSD() {
        return indexTempOnSSD;
    }

    public File getIndexTemp() {
        return indexTemp;
    }

    public boolean isOutputOnSSD() {
        return outputOnSSD;
    }

    public int getNumThreads() {
        return numThreads;
    }

    public File getHashDbFile() {
        return hashDbFile;
    }

    public void setHashDbFile(File hashDbFile) {
        this.hashDbFile = hashDbFile;
    }

    public static void clearLocalParameters(File localConfig) throws IOException {
        List<String> lines = Files.readAllLines(localConfig.toPath());
        ArrayList<String> newLines = new ArrayList<>();
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith(NUM_THREADS)) {
                line = NUM_THREADS + " = " + DEFAULT_VAL;
            } else if (line.startsWith(TEMP_ON_SSD)) {
                line = TEMP_ON_SSD + " = false";
            } else if (line.startsWith(IPED_TEMP)) {
                line = IPED_TEMP + " = " + DEFAULT_VAL;
            } else if (line.startsWith(HASH_DB)) {
                line = "#" + line;
            } else if (line.startsWith(OUTPUT_ON_SSD)) {
                line = OUTPUT_ON_SSD + " = false";
            }
            newLines.add(line);
        }
        Files.write(localConfig.toPath(), newLines);
    }
}
