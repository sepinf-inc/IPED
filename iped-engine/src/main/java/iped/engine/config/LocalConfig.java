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

    public static final String NUM_THREADS = "numThreads";

    public static final String HASH_DB = "hashesDB";

    public static final String IPED_TEMP = "indexTemp";

    private static final String TEMP_ON_SSD = "indexTempOnSSD";

    public static final String OUTPUT_ON_SSD = "outputOnSSD";

    public static final String DEFAULT_VAL = "default";

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

        if (System.getProperty("java.io.basetmpdir") == null) { //$NON-NLS-1$
            System.setProperty("java.io.basetmpdir", System.getProperty("java.io.tmpdir")); //$NON-NLS-1$ //$NON-NLS-2$
        }

        String value;

        File newTmp = null, tmp = new File(System.getProperty("java.io.basetmpdir")); //$NON-NLS-1$

        value = properties.getProperty(IPED_TEMP); // $NON-NLS-1$
        if (value != null) {
            value = value.trim();
        }
        if (ipedTemp == null) {
            if (value != null && !value.equalsIgnoreCase(DEFAULT_VAL)) { // $NON-NLS-1$
                newTmp = new File(value);
                if (!newTmp.exists() && !newTmp.mkdirs()) {
                    if (logger != null)
                        logger.info("Fail to create temp directory" + newTmp.getAbsolutePath()); //$NON-NLS-1$
                } else {
                    tmp = newTmp;
                }
            }
            Random rand = new Random();
            ipedTemp = new File(tmp, "iped-temp" + rand.nextLong()); //$NON-NLS-1$
            if (!ipedTemp.mkdirs()) {
                tmp = new File(System.getProperty("java.io.basetmpdir")); //$NON-NLS-1$
                ipedTemp = new File(tmp, "iped-temp" + rand.nextLong()); //$NON-NLS-1$
                ipedTemp.mkdirs();
            }
            if (ipedTemp.exists()) {
                System.setProperty("java.io.tmpdir", ipedTemp.getAbsolutePath()); //$NON-NLS-1$
            }
            if (tmp == newTmp) {
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
            numThreads = getDefaultNumThreads();
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

    public void setIndexTempOnSSD(Boolean indexTempOnSSD){
        properties.setProperty(TEMP_ON_SSD, Boolean.valueOf(indexTempOnSSD).toString());
        this.indexTempOnSSD = Boolean.valueOf(indexTempOnSSD);
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

    public void setNumThreads(Object numThreads) {
        if (numThreads instanceof Integer) {
            setNumThreads(((Integer) numThreads).intValue());
            return;
        } else {
            if (numThreads instanceof String) {
                if (numThreads.equals(DEFAULT_VAL)) {
                    properties.setProperty(NUM_THREADS, DEFAULT_VAL);
                    return;
                }
            }
        }
    }

    public void setNumThreads(int numThreads){
        this.numThreads = numThreads;
        String stringValue = (numThreads == Runtime.getRuntime().availableProcessors()) ? DEFAULT_VAL : String.valueOf(numThreads);
        properties.setProperty(NUM_THREADS, stringValue);
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

    @Override
    public void save(Path resource) {
        try {
            File confDir = new File(resource.toFile(), Configuration.CONF_DIR);
            confDir.mkdirs();
            File confFile = new File(confDir, CONFIG_FILE);
            properties.store(confFile);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getDefaultNumThreads() {
        return Runtime.getRuntime().availableProcessors();
    }

}
