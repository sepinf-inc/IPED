package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.DirectoryStream.Filter;

import macee.core.Configurable;

public class ExternalParsersConfig implements Configurable<String> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final String EXTERNAL_PARSERS = "ExternalParsers.xml"; //$NON-NLS-1$

    private String externalParsersXml;

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

    public String getTmpConfigFilePath() {
        try {
            Path tmp = Files.createTempFile("external-parsers", ".xml");
            Files.write(tmp, externalParsersXml.getBytes(StandardCharsets.UTF_8));
            tmp.toFile().deleteOnExit();
            return tmp.toFile().getAbsolutePath();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}