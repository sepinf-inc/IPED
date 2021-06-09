package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.SignatureConfig;
import dpf.sp.gpinf.indexer.util.IOUtil;
import iped3.IItem;
import iped3.util.MediaTypes;
import macee.core.Configurable;

/**
 * Análise de assinatura utilizando biblioteca Apache Tika.
 */
public class SignatureTask extends AbstractTask {

    private static Logger LOGGER = LoggerFactory.getLogger(SignatureTask.class);

    private static final String[] HFS_ATTR_SUFFIX = { ":DATA", ":DECOMP", ":RSRC" };

    private boolean processFileSignatures = true;
    private Detector detector;

    @Override
    public boolean isEnabled() {
        return true;
    }

    public void process(IItem evidence) {

        if (evidence.isDir()) {
            evidence.setMediaType(MediaType.OCTET_STREAM);
        }

        MediaType type = evidence.getMediaType();
        if (type == null) {
            Metadata metadata = new Metadata();
            metadata.set(Metadata.RESOURCE_NAME_KEY, evidence.getName());
            try {
                if (processFileSignatures) {
                    TikaInputStream tis = null;
                    try {
                        tis = evidence.getTikaStream();
                        type = detector.detect(tis, metadata).getBaseType();

                    } catch (IOException e) {
                        LOGGER.warn("{} Error detecting signature: {} ({} bytes)\t\t{}", //$NON-NLS-1$
                                Thread.currentThread().getName(), evidence.getPath(), evidence.getLength(),
                                e.toString());
                    } finally {
                        // Fecha handle p/ renomear subitem p/ hash posteriormente. Demais itens são
                        // fechados via evidence.dispose()
                        if (evidence.isSubItem()) {
                            IOUtil.closeQuietly(tis);
                        }
                    }
                }

                // Caso seja item office07 cifrado e tenha extensão específica, refina o tipo
                if (type != null && type.toString().equals("application/x-tika-ooxml-protected") //$NON-NLS-1$
                        && "docx xlsx pptx".contains(evidence.getExt().toLowerCase())) { //$NON-NLS-1$
                    type = MediaType.application("x-tika-ooxml-protected-" + evidence.getExt().toLowerCase()); //$NON-NLS-1$
                }

                if (type == null) {
                    type = detector.detect(null, metadata).getBaseType();

                    // workaround for #197. Should we check TSK_FS_META_FLAG_ENUM?
                    int i = 0;
                    while (MediaType.OCTET_STREAM.equals(type) && i < HFS_ATTR_SUFFIX.length) {
                        String suffix = HFS_ATTR_SUFFIX[i++];
                        if (evidence.getName().endsWith(suffix)) {
                            String name = evidence.getName().substring(0, evidence.getName().lastIndexOf(suffix));
                            metadata.set(Metadata.RESOURCE_NAME_KEY, name);
                            type = detector.detect(null, metadata).getBaseType();
                        }
                    }

                }

            } catch (Exception | OutOfMemoryError e) {
                type = MediaType.OCTET_STREAM;

                LOGGER.warn("{} Error detecting signature: {} ({} bytes)\t\t{}", Thread.currentThread().getName(), //$NON-NLS-1$
                        evidence.getPath(), evidence.getLength(), e.toString());
            }
        }
        evidence.setMediaType(MediaTypes.getMediaTypeRegistry().normalize(type));
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new SignatureConfig());
    }

    @Override
    public void init(Properties confProps, File confDir) throws Exception {
        SignatureConfig config = ConfigurationManager.findObject(SignatureConfig.class);
        System.setProperty(MimeTypesFactory.CUSTOM_MIMES_SYS_PROP, config.getTmpConfigFile().getAbsolutePath());
        // check if setting property above works
        if (MediaTypes.getParentType(MediaType.parse("message/x-chat-message")).equals(MediaType.OCTET_STREAM)) {
            throw new Exception("Custom signature file not loaded!");
        }
        processFileSignatures = config.isEnabled();
        detector = TikaConfig.getDefaultConfig().getDetector();
    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub

    }

}
