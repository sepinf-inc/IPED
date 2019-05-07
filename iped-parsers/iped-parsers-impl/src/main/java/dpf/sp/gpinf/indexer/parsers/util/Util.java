package dpf.sp.gpinf.indexer.parsers.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

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
import iped3.io.ItemBase;
import iped3.search.ItemSearcher;

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

    private static String getContentPreview(InputStream is, Metadata m, boolean isHtml) throws IOException {
        LimitedContentHandler contentHandler = new LimitedContentHandler(MAX_PREVIEW_SIZE);
        TextContentHandler textHandler = new TextContentHandler(contentHandler, true);
        if (m == null)
            m = new Metadata();
        if (isHtml) {
            m.set(Metadata.CONTENT_TYPE, "text/html"); //$NON-NLS-1$
            m.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "text/html"); //$NON-NLS-1$
        }
        boolean limitReached = false;
        try {
            autoParser.parse(is, textHandler, m, new ParseContext());

        } catch (TikaException e) {

        } catch (SAXException se) {
            if (contentHandler.isWriteLimitReached(se))
                limitReached = true;
        }
        String msg = contentHandler.toString().trim();
        if (limitReached)
            msg += "(...)"; //$NON-NLS-1$
        return msg;
    }

    public static String getContentPreview(InputStream is, boolean isHtml) throws IOException {
        return getContentPreview(is, null, isHtml);
    }

    public static String getContentPreview(String content, boolean isHtml) throws IOException {
        Metadata m = new Metadata();
        m.set(KNOWN_CONTENT_ENCODING, "UTF-8"); //$NON-NLS-1$
        return getContentPreview(new ByteArrayInputStream(content.getBytes("UTF-8")), m, isHtml); //$NON-NLS-1$
    }

    public static String getContentPreview(byte[] content, boolean isHtml) throws IOException {
        return getContentPreview(new ByteArrayInputStream(content), null, isHtml);
    }

    public static String decodeUnknowCharset(byte[] data) {

        try {
            int count0 = 0, max = 10000;
            if (data.length < max)
                max = data.length;

            for (int i = 0; i < max; i++)
                if (data[i] == 0)
                    count0++;
            if (count0 > 0 && count0 * 2 >= 0.9 * (float) max)
                return new String(data, "UTF-16LE"); //$NON-NLS-1$

            boolean hasUtf8 = false;
            for (int i = 0; i < max - 1; i++)
                if (data[i] == (byte) 0xC3 && data[i + 1] >= (byte) 0x80 && data[i + 1] <= (byte) 0xBC) {
                    hasUtf8 = true;
                    break;
                }
            if (hasUtf8)
                return new String(data, "UTF-8"); //$NON-NLS-1$

            return new String(data, "windows-1252"); //$NON-NLS-1$

        } catch (UnsupportedEncodingException e) {
            return new String(data);
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

    public static List<ItemBase> getItems(String query, ItemSearcher searcher) {
        if (searcher == null)
            return Collections.emptyList();
        List<ItemBase> items = searcher.search(query);
        return items;
    }

    public static String getExportPath(ItemBase item) {
        String hash = item.getHash();
        String ext = "." + item.getExt(); //$NON-NLS-1$
        return getExportPath(hash, ext);
    }

    public static String getExportPath(String hash, String ext) {
        if (hash == null || hash.length() < 2)
            return ""; //$NON-NLS-1$
        return "../../" + hash.charAt(0) + "/" + hash.charAt(1) + "/" + hash + ext; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    public static byte[] getPreview(ItemBase item) {
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

    public static String getThumbPath(ItemBase item) {
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
