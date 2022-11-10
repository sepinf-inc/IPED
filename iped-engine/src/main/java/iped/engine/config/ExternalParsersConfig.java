package iped.engine.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;

import iped.configuration.Configurable;

public class ExternalParsersConfig implements Configurable<String> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final String EXTERNAL_PARSERS = "ExternalParsers.xml"; //$NON-NLS-1$

    private String externalParsersXml;
    private transient Path tmp;

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return entry.endsWith(EXTERNAL_PARSERS);
            }
        };
    }

    @Override
    public void processConfig(Path resource) throws IOException {
        externalParsersXml = new String(Files.readAllBytes(resource), StandardCharsets.UTF_8);
    }

    @Override
    public String getConfiguration() {
        return externalParsersXml;
    }

    @Override
    public void setConfiguration(String config) {
        externalParsersXml = config;
    }

    public synchronized String getTmpConfigFilePath() {
        if (tmp == null) {
            try {
                tmp = Files.createTempFile("external-parsers", ".xml");
                Files.write(tmp, externalParsersXml.getBytes(StandardCharsets.UTF_8));
                tmp.toFile().deleteOnExit();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return tmp.toFile().getAbsolutePath();
    }

    @Override
    public void save(Path resource) {
        try {
            File confDir = new File(resource.toFile(), Configuration.CONF_DIR);
            confDir.mkdirs();
            File confFile = new File(confDir, EXTERNAL_PARSERS);            

            Files.write(confFile.toPath(), externalParsersXml.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}