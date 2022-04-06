package dpf.sp.gpinf.indexer.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SignatureConfig extends AbstractTaskConfig<String> {

    /**
     * 
     */
    private static final long serialVersionUID = -412134138223527641L;

    private static final String ENABLE_PARAM = "processFileSignatures";
    public static final String CUSTOM_MIMES_CONFIG = "CustomSignatures.xml"; //$NON-NLS-1$

    private String customSignaturesXml;
    private transient Path tmp;

    @Override
    public String getConfiguration() {
        return customSignaturesXml;
    }

    public synchronized File getTmpConfigFile() {
        if (tmp == null) {
            try {
                tmp = Files.createTempFile("custom-signatures", ".xml");
                Files.write(tmp, customSignaturesXml.getBytes(StandardCharsets.UTF_8));
                tmp.toFile().deleteOnExit();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return tmp.toFile();
    }

    @Override
    public void setConfiguration(String config) {
        customSignaturesXml = config;
    }

    @Override
    public String getTaskEnableProperty() {
        return ENABLE_PARAM;
    }

    @Override
    public String getTaskConfigFileName() {
        return CUSTOM_MIMES_CONFIG;
    }

    @Override
    public void processTaskConfig(Path resource) throws IOException {
        customSignaturesXml = new String(Files.readAllBytes(resource), StandardCharsets.UTF_8);
    }

}
