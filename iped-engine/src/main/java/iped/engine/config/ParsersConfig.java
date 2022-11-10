package iped.engine.config;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;

import iped.configuration.Configurable;

public class ParsersConfig implements Configurable<String> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final String PARSER_CONFIG = "ParserConfig.xml"; //$NON-NLS-1$

    private String parserConfigXml;
    private transient Path tmp;

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return entry.endsWith(PARSER_CONFIG);
            }
        };
    }

    @Override
    public void processConfig(Path resource) throws IOException {
        parserConfigXml = new String(Files.readAllBytes(resource), StandardCharsets.UTF_8);
    }

    @Override
    public String getConfiguration() {
        return parserConfigXml;
    }

    @Override
    public void setConfiguration(String config) {
        parserConfigXml = config;
    }

    public synchronized File getTmpConfigFile() {
        if (tmp == null) {
            try {
                tmp = Files.createTempFile("parser-config", ".xml");
                Files.write(tmp, parserConfigXml.getBytes(StandardCharsets.UTF_8));
                tmp.toFile().deleteOnExit();

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return tmp.toFile();
    }

    @Override
    public void save(Path resource) {
        try {
            File confDir = new File(resource.toFile(), Configuration.CONF_DIR);
            confDir.mkdirs();
            File confFile = new File(confDir, PARSER_CONFIG);            

            Files.write(confFile.toPath(), parserConfigXml.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
