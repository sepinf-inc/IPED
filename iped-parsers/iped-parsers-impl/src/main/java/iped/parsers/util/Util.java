package iped.parsers.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.TextContentHandler;
import org.apache.tika.sax.ToTextContentHandler;
import org.slf4j.Logger;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.data.IItem;
import iped.data.IItemReader;
import iped.parsers.standard.RawStringParser;
import iped.parsers.standard.StandardParser;
import iped.search.IItemSearcher;
import iped.utils.IOUtil;

public class Util {

    private static final String imageThumbsDir = "../../../../iped/thumbs/"; //$NON-NLS-1$
    private static final String videoThumbsDir = "../../../../iped/view/"; //$NON-NLS-1$
    private static final int MAX_PREVIEW_SIZE = 128;
    public static final String KNOWN_CONTENT_ENCODING = "KNOWN-CONTENT-ENCODING"; //$NON-NLS-1$

    private static StandardParser autoParser;
    private static TikaConfig tikaConfig;

    private static StandardParser getAutoParser() {
        if (autoParser == null) {
            autoParser = new StandardParser();
            autoParser.setErrorParser(null);
            autoParser.setPrintMetadata(false);
        }
        return autoParser;
    }

    private static String getContentPreview(InputStream is, Metadata m, String mimeType) {
        LimitedContentHandler contentHandler = new LimitedContentHandler(MAX_PREVIEW_SIZE);
        TextContentHandler textHandler = new TextContentHandler(contentHandler, true);
        if (m == null)
            m = new Metadata();
        if (mimeType != null && !mimeType.isEmpty()) {
            m.set(Metadata.CONTENT_TYPE, mimeType); // $NON-NLS-1$
            m.set(StandardParser.INDEXER_CONTENT_TYPE, mimeType); // $NON-NLS-1$
        }
        boolean limitReached = false;
        try {
            getAutoParser().parse(is, textHandler, m, new ParseContext());

        } catch (TikaException | IOException e) {
            e.printStackTrace();

        } catch (SAXException se) {
            if (contentHandler.isWriteLimitReached(se))
                limitReached = true;
        }
        String msg = contentHandler.toString().trim();
        if (limitReached)
            msg += "(...)"; //$NON-NLS-1$
        return msg;
    }

    public static String getContentPreview(InputStream is, String mimeType) throws IOException {
        return getContentPreview(is, null, mimeType);
    }

    public static String getContentPreview(String content, String mimeType) throws IOException {
        Metadata m = new Metadata();
        m.set(KNOWN_CONTENT_ENCODING, "UTF-8"); //$NON-NLS-1$
        m.set(Metadata.CONTENT_ENCODING, "UTF-8"); //$NON-NLS-1$
        return getContentPreview(new ByteArrayInputStream(content.getBytes("UTF-8")), m, mimeType); //$NON-NLS-1$
    }

    public static String getContentPreview(byte[] content, String mimeType) throws IOException {
        return getContentPreview(new ByteArrayInputStream(content), null, mimeType);
    }

    private static String decodeUTF16OrUTF8(byte[] data) throws UnsupportedEncodingException {

        int count0 = 0, max = 1 << 14;
        if (data.length < max) {
            max = data.length;
        }
        for (int i = 0; i < max; i++) {
            if (data[i] == 0) {
                count0++;
            }
        }
        int count = 2 * count0;
        if (count > 0 && count >= 0.9 * (float) max && count <= 1.1 * (float) max) {
            return new String(data, StandardCharsets.UTF_16LE);
        }

        String result = new String(data, StandardCharsets.UTF_8);

        if (result.contains("�")) {
            throw new UnsupportedEncodingException("Data is not UTF8 nor UTF16");
        }

        return result;
    }

    public static String decodeUnknownCharset(byte[] data) {
        try {
            return decodeUTF16OrUTF8(data);

        } catch (UnsupportedEncodingException e) {
            return decodeWindows1252(data);
        }
    }

    private static String decodeWindows1252(byte[] data) {
        try {
            return new String(data, "windows-1252");

        } catch (UnsupportedEncodingException e1) {
            return new String(data, StandardCharsets.ISO_8859_1);
        }
    }

    public static String decodeUnknownCharsetSimpleThenTika(byte[] data) {
        try {
            return decodeUTF16OrUTF8(data);

        } catch (UnsupportedEncodingException e) {

            return decodeUnknownCharsetTika(data, false);
        }
    }

    public static String decodeUnknownCharsetTikaThenSimple(byte[] data) {
        return decodeUnknownCharsetTika(data, true);
    }

    private static String decodeUnknownCharsetTika(byte[] data, boolean useFallbackDetection) {
        try (Reader reader = new AutoDetectReader(new ByteArrayInputStream(data))) {
            int i = 0;
            char[] cbuf = new char[1 << 12];
            StringBuilder sb = new StringBuilder();
            while ((i = reader.read(cbuf)) != -1) {
                sb.append(cbuf, 0, i);
            }
            return sb.toString();

        } catch (IOException | TikaException e) {
            if (useFallbackDetection) {
                return decodeUnknownCharset(data);
            } else {
                return decodeWindows1252(data);
            }
        }
    }

    public static String decodeMixedCharset(byte[] data) {
        ToTextContentHandler handler = new ToTextContentHandler();
        try {
            new RawStringParser().parse(new ByteArrayInputStream(data), handler, new Metadata(), null);
            return handler.toString();

        } catch (Exception e) {
            return new String(data);
        }
    }

    public static void waitFor(Process p, ContentHandler handler) throws InterruptedException {

        ContainerVolatile msg = new ContainerVolatile();
        ignoreStream(p.getInputStream(), msg);

        while (true) {
            try {
                p.exitValue();
                break;
            } catch (Exception e) {
            }

            if (msg.progress == true && handler != null)
                try {
                    handler.characters(" ".toCharArray(), 0, 1); //$NON-NLS-1$
                    // System.out.println("progress");
                } catch (SAXException e) {
                }

            msg.progress = false;

            Thread.sleep(1000);

        }
    }

    static class ContainerVolatile {
        volatile boolean progress = false;
    }

    public static void ignoreStream(final InputStream stream) {
        ignoreStream(stream, null);
    }

    public static void ignoreStream(final InputStream stream, final ContainerVolatile msg) {
        Thread t = new Thread() {
            @Override
            public void run() {
                byte[] out = new byte[1024];
                int read = 0;
                try {
                    while (read != -1) {
                        read = stream.read(out);
                        if (msg != null) {
                            msg.progress = true;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }
    
    public static void logInputStream(final InputStream stream, final Logger logger) {
        Thread t = new Thread() {
            @Override
            public void run() {
                byte[] out = new byte[1024];
                int read = 0;
                try {
                    while (read != -1) {
                        read = stream.read(out);
                        if (read > 0) {
                            logger.warn(new String(out, 0, read));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }

    public static List<IItemReader> getItems(String query, IItemSearcher searcher) {
        if (searcher == null)
            return Collections.emptyList();
        List<IItemReader> items = searcher.search(query);
        return items;
    }

    public static String getExportPath(IItemReader item) {
        String hash = item.getHash();
        String ext = item.getType(); // $NON-NLS-1$
        return getExportPath(hash, ext);
    }

    public static String getExportPath(String hash, String ext) {
        if (hash == null || hash.length() < 2)
            return ""; //$NON-NLS-1$
        StringBuilder sb = new StringBuilder();
        sb.append("../../").append(hash.charAt(0)).append("/").append(hash.charAt(1)).append("/").append(hash);
        if (ext != null && !ext.isEmpty())
            sb.append(".").append(ext);
        return sb.toString();
    }

    public static String getReportHref(IItemReader item) {
        String exportPath = getExportPath(item);
        String originalPath = getSourceFileIfExists(item).orElse("");
        StringBuilder sb = new StringBuilder();
        sb.append("javascript:open");
        String type = item.getMediaType().getType();
        if (type.equals("image")) {
            sb.append("Image");
        } else if (type.equals("audio")) {
            sb.append("Audio");
        } else if (type.equals("video")) {
            sb.append("Video");
        } else {
            sb.append("Other");
        }
        sb.append("('");
        sb.append(exportPath);
        sb.append("','");
        sb.append(originalPath);
        sb.append("')");
        return sb.toString();
    }

    public static String getSourceFileIfExists(String originalPath) {
        File file;
        if (originalPath != null && (file = new File(originalPath)).exists()) {
            String path = normalizePath(file);
            if (path != null) {
                return ajustPath(path);
            }
        }
        return null;
    }

    public static Optional<String> getSourceFileIfExists(IItemReader item) {
        if (IOUtil.hasFile(item)) {
            String path = normalizePath(IOUtil.getFile(item));
            if (path != null) {
                path = ajustPath(path);
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }

    private static String ajustPath(String path) {
        path = path.replaceAll("\\\\", "/");
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.length() > 2 && path.charAt(1) == ':') {
            path = "file:///" + path;
        }
        return path;
    }

    private static String normalizePath(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        return file.toPath().toAbsolutePath().normalize().toString();
    }

    public static String getParentPath(String path) {
        int i = path.lastIndexOf("\\"); //$NON-NLS-1$
        if (i == -1)
            i = path.lastIndexOf("/"); //$NON-NLS-1$
        if (i <= 0)
            return null;
        if (i == path.length() - 1)
            return getParentPath(path.substring(0, path.length() - 1));
        else
            return path.substring(0, i);
    }

    private static TikaConfig getTikaConfig() throws TikaException, IOException {
        if (tikaConfig == null) {
            synchronized (Util.class) {
                if (tikaConfig == null) {
                    tikaConfig = new TikaConfig();
                }
            }
        }
        return tikaConfig;
    }

    public static String getTrueExtension(File file) {
        String trueExt = "";
        String origExt = "";
        try {
            int idx = file.getName().lastIndexOf('.');
            if (idx != -1) {
                origExt = file.getName().substring(idx);
            }
            Metadata meta = new Metadata();
            MediaType mediaType = MediaType.OCTET_STREAM;
            try (TikaInputStream in = TikaInputStream.get(file.toPath(), meta)) {
                mediaType = getTikaConfig().getDetector().detect(in, meta);
            }

            trueExt = getTrueExtension(origExt, mediaType);

            if (trueExt.startsWith(".")) {
                trueExt = trueExt.substring(1);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return trueExt;
    }

    public static String getTrueExtension(String origExt, MediaType mediaType) throws TikaException, IOException {
        String trueExt = "";
        if (!mediaType.equals(MediaType.OCTET_STREAM)) {
            do {
                boolean first = true;
                for (String ext : getTikaConfig().getMimeRepository().forName(mediaType.toString()).getExtensions()) {
                    if (first) {
                        trueExt = ext;
                        first = false;
                    }
                    if (ext.equals(origExt)) {
                        trueExt = origExt;
                        break;
                    }
                }

            } while (trueExt.isEmpty() && !MediaType.OCTET_STREAM
                    .equals((mediaType = getTikaConfig().getMediaTypeRegistry().getSupertype(mediaType))));
        }

        if (!origExt.isEmpty() && (trueExt.isEmpty() || trueExt.equals(".txt"))) { //$NON-NLS-1$
            trueExt = origExt;
        }
        return trueExt.toLowerCase();
    }

    public static File getFileRenamedToExt(File file, String ext) {
        if (!ext.isEmpty() && !file.getName().endsWith("." + ext)) {
            File renamedFile = new File(file.getAbsolutePath() + "." + ext);
            if (renamedFile.exists() || file.renameTo(renamedFile)) {
                return renamedFile;
            }
        }
        return file;
    }

    public static File getFileWithRightExt(IItem item) throws IOException {
        File file = item.getTempFile();
        String ext = item.getType();
        boolean isTmpFile = IOUtil.isTemporaryFile(file);
        boolean badExt = !ext.isEmpty() && !file.getName().endsWith("." + ext);
        if (!isTmpFile && badExt) {
            File tmp = File.createTempFile("iped", "." + ext);
            tmp.deleteOnExit();
            IOUtil.copyFile(file, tmp);
            return tmp;
        } else {
            if (isTmpFile) {
                file.deleteOnExit();
                if (badExt) {
                    file = Util.getFileRenamedToExt(file, ext);
                }
            } else {
                file.setReadOnly();
            }
            return file;
        }
    }

}
