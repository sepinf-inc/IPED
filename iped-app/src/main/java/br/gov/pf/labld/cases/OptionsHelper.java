package br.gov.pf.labld.cases;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;

public class OptionsHelper {

    private Properties props;

    public OptionsHelper(String fileName) {
        super();
        this.props = getProperties(fileName);
    }

    private Properties getProperties(String fileName) {
        File configFile = getFile(fileName);
        Properties props = new Properties();
        try (Reader in = new InputStreamReader(new BufferedInputStream(new FileInputStream(configFile)),
                Charset.forName("utf-8"))) {
            props.load(in);
        } catch (IOException e) {
            // Nothing to do.
        }
        return props;
    }

    private static File getFile(String fileName) {
        File file = new File(getRootDir(), fileName);
        return file;
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(props.getProperty(key, "false"));
    }

    public static File writeToFile(String fileName, Map<String, Object> properties) throws IOException {
        File configFile = getFile(fileName);
        if (!configFile.exists()) {
            configFile.createNewFile();
        }
        String fileContent;
        try (Reader in = new InputStreamReader(new BufferedInputStream(new FileInputStream(configFile)))) {
            fileContent = IOUtils.toString(in);
        }
        for (Entry<String, Object> entry : properties.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            Pattern pattern = Pattern.compile(name + "\\s*=\\s*(.+)");
            Matcher matcher = pattern.matcher(fileContent);

            String newProp = name + " = " + value;
            if (matcher.find()) {
                fileContent = matcher.replaceAll(newProp);
            } else {
                fileContent += "\r\n" + newProp;
            }
        }
        try (Writer out = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(configFile)),
                Charset.forName("utf-8"))) {
            IOUtils.write(fileContent, out);
        }
        return configFile;
    }

    private static File getRootDir() {
        try {
            URI uri = OptionsHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            File rootDir = Paths.get(uri).normalize().toFile().getParentFile();
            return rootDir;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

}
