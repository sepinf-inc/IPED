package iped.engine.task;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.SignatureConfig;
import iped.io.SeekableInputStream;
import iped.properties.MediaTypes;
import iped.utils.IOUtil;
import iped.utils.SimpleInputStreamFactory;

/**
 * Signature analysis using the Apache Tika library.
 */
public class SignatureTask extends AbstractTask {

    private static Logger LOGGER = LoggerFactory.getLogger(SignatureTask.class);

    private static final String[] HFS_ATTR_SUFFIX = { ":DATA", ":DECOMP", ":RSRC" };

    private boolean processFileSignatures = true;
    private Detector detector;

    private static final String MAGIC_ONLY_ATTR = "magic-only";
    private static Set<MediaType> magicOnlyTypes;

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
            String name = evidence.getName(); 
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
            try {
                if (processFileSignatures) {
                    TikaInputStream tis = null;
                    try {
                        tis = evidence.getTikaStream();
                        // See https://github.com/sepinf-inc/IPED/issues/2356
                        setInputStreamFactory(tis, evidence);

                        type = getDetector().detect(tis, metadata).getBaseType();

                        if (magicOnlyTypes.contains(type)) {
                            // Repeat detection without item's name
                            IOUtil.closeQuietly(tis);
                            tis = evidence.getTikaStream();
                            Metadata empty = new Metadata();
                            type = getDetector().detect(tis, empty).getBaseType();
                        }

                    } catch (IOException e) {
                        LOGGER.warn("{} Error detecting signature: {} ({} bytes)\t\t{}", //$NON-NLS-1$
                                Thread.currentThread().getName(), evidence.getPath(), evidence.getLength(),
                                e.toString());
                    } finally {
                        // Close handle to rename subitem to hash later. Other items are
                        // closed via evidence.dispose()
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

                // If it is an encrypted Office 2007 item and has a specific extension, refine the type
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
                        if (name.endsWith(suffix)) {
                            name = name.substring(0, name.lastIndexOf(suffix));
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
        if (processFileSignatures) {
            synchronized (MAGIC_ONLY_ATTR) {
                if (magicOnlyTypes == null) {
                    magicOnlyTypes = loadMagicOnlyTypes();
                }
            }
        }
    }

    public static void installCustomSignatures() {
        SignatureConfig config = ConfigurationManager.get().findObject(SignatureConfig.class);
        System.setProperty(MimeTypesFactory.CUSTOM_MIMES_SYS_PROP, config.getTmpConfigFile().getAbsolutePath());
    }

    @Override
    public void finish() throws Exception {
    }

    private static Set<MediaType> loadMagicOnlyTypes() {
        SignatureConfig config = ConfigurationManager.get().findObject(SignatureConfig.class);
        String file = config.getTmpConfigFile().getAbsolutePath();

        Set<MediaType> result = new HashSet<MediaType>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            try (InputStream is = new FileInputStream(file)) {
                Document doc = builder.parse(is);
                NodeList mimeNodes = doc.getElementsByTagName("mime-type");

                for (int i = 0; i < mimeNodes.getLength(); i++) {
                    Element el = (Element) mimeNodes.item(i);
                    if ("true".equalsIgnoreCase(el.getAttribute(MAGIC_ONLY_ATTR))) {
                        MediaType parsed = MediaType.parse(el.getAttribute("type"));
                        if (parsed != null) {
                            result.add(parsed.getBaseType());
                        }
                    }
                }
            }
            LOGGER.info("{} types: {}", MAGIC_ONLY_ATTR, result);
        } catch (Exception e) {
            throw new RuntimeException("Fail loading '" + MAGIC_ONLY_ATTR + "' attributes from " + file, e);
        }
        return result;
    }
}
