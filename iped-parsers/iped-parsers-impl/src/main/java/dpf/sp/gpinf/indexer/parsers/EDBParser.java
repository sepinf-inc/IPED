package dpf.sp.gpinf.indexer.parsers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.util.Util;
import dpf.sp.gpinf.indexer.util.SimpleHTMLEncoder;

public class EDBParser extends AbstractParser {

    /**
     * 
     */
    private static final long serialVersionUID = 1938967091470933178L;
    private static Logger LOGGER = LoggerFactory.getLogger(EDBParser.class);
    private static Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("x-edb")); //$NON-NLS-1$
    private static String TOOL_NAME = "esedbexport"; //$NON-NLS-1$
    private static String OUT_SUFFIX = ".export"; //$NON-NLS-1$
    private static boolean tested = false;
    private static String TABLE_PREFIX = "Table"; //$NON-NLS-1$

    private RawStringParser rawParser = new RawStringParser();

    public static final String TOOL_PATH_PROP = TOOL_NAME + ".path"; //$NON-NLS-1$
    private String TOOL_PATH = System.getProperty(TOOL_PATH_PROP, ""); //$NON-NLS-1$

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {

        synchronized (this.getClass()) {
            if (!tested)
                try {
                    String[] cmd = { TOOL_PATH + TOOL_NAME, "-V" }; //$NON-NLS-1$
                    Process p = Runtime.getRuntime().exec(cmd);
                    p.waitFor();
                    if (p.exitValue() != 0)
                        throw new Exception();

                } catch (Exception e) {
                    LOGGER.error("Error testing esedbexport (libesedb): EDB databases will NOT be parsed!"); //$NON-NLS-1$
                    SUPPORTED_TYPES = Collections.EMPTY_SET;
                }
            tested = true;
        }

        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        TemporaryResources tmp = new TemporaryResources();
        File file = null;
        try {
            TikaInputStream tis = TikaInputStream.get(stream, tmp);
            file = tis.getFile();

            // indexa strings brutas como garantia, caso expansão seja incompleta
            rawParser.parse(tis, handler, metadata, context);

            File tmpFile = tmp.createTemporaryFile();

            String[] cmd = { TOOL_PATH + TOOL_NAME, "-t", tmpFile.getAbsolutePath(), file.getAbsolutePath() }; //$NON-NLS-1$
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            try {
                // p.waitFor();
                Util.waitFor(p, xhtml);

            } catch (InterruptedException e) {
                p.destroyForcibly();
                throw new TikaException(this.getClass().getSimpleName() + " interrupted", e); //$NON-NLS-1$
            }

            File tmpDir = new File(tmpFile.getAbsolutePath() + OUT_SUFFIX);
            File[] tables = tmpDir.listFiles();
            if (tables != null)
                for (File table : tables) {

                    Metadata tableMetadata = new Metadata();
                    tableMetadata.set(Metadata.RESOURCE_NAME_KEY, TABLE_PREFIX + "-" + table.getName()); //$NON-NLS-1$

                    if (metadata.get(Metadata.CONTENT_TYPE).endsWith("x-webcache")) //$NON-NLS-1$
                        tableMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "application/x-webcache-table"); //$NON-NLS-1$

                    if (extractor.shouldParseEmbedded(tableMetadata)) {
                        File htmlTable = processTable(table, tmp, extractor, xhtml);
                        try (InputStream is = new FileInputStream(htmlTable)) {
                            extractor.parseEmbedded(is, xhtml, tableMetadata, true);
                        }
                    }
                    table.delete();
                }
            tmpDir.delete();

        } catch (Exception e) {
            if (e instanceof IOException && file == null)
                throw (IOException) e;
            e.printStackTrace();
            throw new TikaException("EDBParser error", e); //$NON-NLS-1$

        } finally {
            tmp.close();
        }

        xhtml.endDocument();

    }

    private File processTable(File tabFile, TemporaryResources tmp, EmbeddedDocumentExtractor extractor,
            ContentHandler handler) throws IOException, SAXException {
        Charset charset = Charset.forName("UTF-8"); //$NON-NLS-1$
        File html = tmp.createTemporaryFile();
        try (BufferedReader reader = new BufferedReader(new FileReader(tabFile));
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(html), charset));) {
            writer.write("<html>"); //$NON-NLS-1$
            writer.write("<!--EDB Extracted Table-->"); //$NON-NLS-1$
            writer.write("<head>"); //$NON-NLS-1$
            writer.write("<meta http-equiv=\"content-type\" content=\"text/html; charset=" + charset + "\" />"); //$NON-NLS-1$ //$NON-NLS-2$

            // Torna renderização mto mais lenta
            /*
             * writer.println("<style>"); writer.
             * println("table {border-collapse: collapse; table-layout:fixed;} table, td, th {border: 1px solid black; word-wrap:break-word;}"
             * ); writer.println("</style>");
             */

            writer.write("</head>"); //$NON-NLS-1$

            writer.write("<body>"); //$NON-NLS-1$
            writer.write("<table>\n"); //$NON-NLS-1$

            String line;
            int row = 0;
            boolean header = true;
            HashMap<Integer, String> colnames = new HashMap<Integer, String>();
            while ((line = reader.readLine()) != null) {
                writer.write("<tr>"); //$NON-NLS-1$
                String[] values = line.split("\t"); //$NON-NLS-1$
                for (int i = 0; i < values.length; i++) {
                    String value = values[i].trim();
                    writer.write("<td>"); //$NON-NLS-1$
                    if (header) {
                        writer.write("<b>"); //$NON-NLS-1$
                        colnames.put(i, value);
                    }

                    if (value.length() <= 128 || !isHexBinary(value))
                        writer.write(SimpleHTMLEncoder.htmlEncode(value));
                    else {
                        String name = TABLE_PREFIX + "_" + tabFile.getName() + "_col-" + colnames.get(i) + "_row" + row //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                + ".data"; //$NON-NLS-1$
                        writer.write(SimpleHTMLEncoder.htmlEncode(name));

                        Metadata meta = new Metadata();
                        meta.set(Metadata.RESOURCE_NAME_KEY, name);
                        try (InputStream is = new ByteArrayInputStream(Hex.decodeHex(value.toCharArray()))) {
                            extractor.parseEmbedded(is, handler, meta, true);

                        } catch (DecoderException e) {
                            e.printStackTrace();
                        }
                    }
                    if (header)
                        writer.write("</b>"); //$NON-NLS-1$
                    writer.write("</td>"); //$NON-NLS-1$
                }
                writer.write("</tr>\n"); //$NON-NLS-1$
                header = false;
                row++;
            }
            writer.write("</table>"); //$NON-NLS-1$
            writer.write("</body>"); //$NON-NLS-1$
            writer.write("</html>"); //$NON-NLS-1$
        }

        return html;
    }

    private boolean isHexBinary(String data) {
        if (data.length() % 2 != 0)
            return false;
        int max = data.length() > 1000 ? 1000 : data.length();
        for (int i = 0; i < max; i++) {
            char c = data.charAt(i);
            if (c < '0' || (c > '9' && c < 'a') || c > 'f')
                return false;
        }
        return true;
    }

}
