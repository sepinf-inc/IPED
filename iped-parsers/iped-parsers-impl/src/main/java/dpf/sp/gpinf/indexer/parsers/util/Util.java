package dpf.sp.gpinf.indexer.parsers.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.TextContentHandler;
import org.apache.tika.sax.ToTextContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.RawStringParser;
import dpf.sp.gpinf.indexer.util.IOUtil;
import iped3.io.IItemBase;
import iped3.search.IItemSearcher;

public class Util {

    private static final String imageThumbsDir = "../../../../indexador/thumbs/"; //$NON-NLS-1$
    private static final String videoThumbsDir = "../../../../indexador/view/"; //$NON-NLS-1$

    private static IndexerDefaultParser autoParser = new IndexerDefaultParser();

    static {
        autoParser.setErrorParser(null);
        autoParser.setPrintMetadata(false);
    }

    private static final int MAX_PREVIEW_SIZE = 128;

    public static final String KNOWN_CONTENT_ENCODING = "KNOWN-CONTENT-ENCODING"; //$NON-NLS-1$

    private static String getContentPreview(InputStream is, Metadata m, String mimeType) {
        LimitedContentHandler contentHandler = new LimitedContentHandler(MAX_PREVIEW_SIZE);
        TextContentHandler textHandler = new TextContentHandler(contentHandler, true);
        if (m == null)
            m = new Metadata();
        if (mimeType != null && !mimeType.isEmpty()) {
            m.set(Metadata.CONTENT_TYPE, mimeType); // $NON-NLS-1$
            m.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, mimeType); // $NON-NLS-1$
        }
        boolean limitReached = false;
        try {
            autoParser.parse(is, textHandler, m, new ParseContext());

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

    public static String decodeUnknowCharset(byte[] data) {
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
                return decodeUnknowCharset(data);
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
                while (read != -1)
                    try {
                        read = stream.read(out);
                        if (msg != null)
                            msg.progress = true;

                    } catch (Exception e) {
                    }
            }
        };
        t.setDaemon(true);
        t.start();
    }

    public static List<IItemBase> getItems(String query, IItemSearcher searcher) {
        if (searcher == null)
            return Collections.emptyList();
        List<IItemBase> items = searcher.search(query);
        return items;
    }

    public static String getExportPath(IItemBase item) {
        String hash = item.getHash();
        String ext = item.getTypeExt(); // $NON-NLS-1$
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

    public static String getReportHref(String hash, String ext, String originalPath) {
        String exportPath = getExportPath(hash, ext);
        String path = getSourceFileIfExists(originalPath);
        return "javascript:openIfExists('" + exportPath + "','" + path + "')";
    }

    public static String getReportHref(IItemBase item) {
        String exportPath = getExportPath(item);
        String originalPath = getSourceFileIfExists(item).orElse("");
        return "javascript:openIfExists('" + exportPath + "','" + originalPath + "')";
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

    public static Optional<String> getSourceFileIfExists(IItemBase item) {
        if (item.hasFile()) {
            String path = normalizePath(item.getFile());
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

    public static byte[] getPreview(IItemBase item) {
        byte[] thumb = null;
        InputStream is = null;
        try {
            String mime = item.getMediaType().toString();
            if (mime.startsWith("image")) //$NON-NLS-1$
                thumb = IOUtil.loadInputStream(is = item.getBufferedStream());
            else if (mime.startsWith("video") && item.getViewFile() != null) //$NON-NLS-1$
                thumb = Files.readAllBytes(item.getViewFile().toPath());

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IOUtil.closeQuietly(is);
        }
        return thumb;
    }

    public static String getThumbPath(IItemBase item) {
        String thumbPath = null;
        String mime = item.getMediaType().toString();
        String hash = item.getHash();
        if (hash != null && hash.length() > 1) {
            if (mime.startsWith("image")) //$NON-NLS-1$
                thumbPath = imageThumbsDir;
            else if (mime.startsWith("video") && item.getViewFile() != null) //$NON-NLS-1$
                thumbPath = videoThumbsDir;
            if (thumbPath != null)
                thumbPath += hash.charAt(0) + "/" + hash.charAt(1) + "/" + hash + ".jpg"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        return thumbPath;
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

}
