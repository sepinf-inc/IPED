package iped.engine.task;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.SignatureConfig;
import iped.io.SeekableInputStream;
import iped.properties.MediaTypes;
import iped.utils.IOUtil;
import iped.utils.SimpleInputStreamFactory;

/**
 * Análise de assinatura utilizando biblioteca Apache Tika.
 */
public class SignatureTask extends AbstractTask {

    private static Logger LOGGER = LoggerFactory.getLogger(SignatureTask.class);

    private static final String[] HFS_ATTR_SUFFIX = { ":DATA", ":DECOMP", ":RSRC" };

    private boolean processFileSignatures = true;
    private Detector detector;

    private Detector getDetector() {
        if (detector == null) {
            // check if custom signatures were loaded correctly
            if (MediaTypes.getParentType(MediaType.parse("message/x-chat-message")).equals(MediaType.OCTET_STREAM)) {
                throw new RuntimeException("Custom signature file not loaded!");
            }
            detector = TikaConfig.getDefaultConfig().getDetector();
        }
        return detector;
    }

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
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, evidence.getName());
            try {
                if (processFileSignatures) {
                    TikaInputStream tis = null;
                    try {
                        tis = evidence.getTikaStream();
                        // See https://github.com/sepinf-inc/IPED/issues/2356
                        setInputStreamFactory(tis, evidence);

                        type = getDetector().detect(tis, metadata).getBaseType();

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

                if (MediaTypes.DISK_IMAGE.equals(type)) {
                    if(hasVHDFooter(evidence)) {
                        type = MediaTypes.VHD;
                    }
                }

                String ext = evidence.getExt() != null ? evidence.getExt().toLowerCase() : "";

                // Caso seja item office07 cifrado e tenha extensão específica, refina o tipo
                if (type != null && type.toString().equals("application/x-tika-ooxml-protected") //$NON-NLS-1$
                        && (ext.equals("docx") || ext.equals("xlsx") || ext.equals("pptx"))) { //$NON-NLS-1$
                    type = MediaType.application("x-tika-ooxml-protected-" + ext); //$NON-NLS-1$
                }

                if (type == null) {
                    type = getDetector().detect(null, metadata).getBaseType();

                    // workaround for #197. Should we check TSK_FS_META_FLAG_ENUM?
                    int i = 0;
                    while (MediaType.OCTET_STREAM.equals(type) && i < HFS_ATTR_SUFFIX.length) {
                        String suffix = HFS_ATTR_SUFFIX[i++];
                        if (evidence.getName().endsWith(suffix)) {
                            String name = evidence.getName().substring(0, evidence.getName().lastIndexOf(suffix));
                            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
                            type = getDetector().detect(null, metadata).getBaseType();
                        }
                    }

                }
            // catch OOME and StackOverflow from Tika, see #768
            } catch (Throwable e) {
                type = MediaType.OCTET_STREAM;

                LOGGER.warn("{} Error detecting signature: {} ({} bytes)\t\t{}", Thread.currentThread().getName(), //$NON-NLS-1$
                        evidence.getPath(), evidence.getLength(), e.toString());
            }
        }
        evidence.setMediaType(MediaTypes.getMediaTypeRegistry().normalize(type));
    }

    private void setInputStreamFactory(TikaInputStream tis, IItem item) {
        tis.setOpenContainer(new SimpleInputStreamFactory() {
            @Override
            public InputStream getInputStream() throws IOException {
                return item.getSeekableInputStream();
            }
        });

    }

    private boolean hasVHDFooter(IItem item) {
        if (item.getLength() == null) {
            return false;
        }
        try (SeekableInputStream is = item.getSeekableInputStream()) {
            is.seek(item.getLength() - 512);
            byte[] cookie = IOUtils.readFully(is, 9);
            if ("conectix".equals(new String(cookie, 0, 8, StandardCharsets.ISO_8859_1))
                    || "conectix".equals(new String(cookie, 1, 8, StandardCharsets.ISO_8859_1))) {
                return true;
            }
        } catch (IOException e) {
            // ignore
        }
        return false;
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new SignatureConfig());
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        installCustomSignatures();
        SignatureConfig config = configurationManager.findObject(SignatureConfig.class);
        processFileSignatures = config.isEnabled();
    }

    public static void installCustomSignatures() {
        SignatureConfig config = ConfigurationManager.get().findObject(SignatureConfig.class);
        System.setProperty(MimeTypesFactory.CUSTOM_MIMES_SYS_PROP, config.getTmpConfigFile().getAbsolutePath());
    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub

    }

}
