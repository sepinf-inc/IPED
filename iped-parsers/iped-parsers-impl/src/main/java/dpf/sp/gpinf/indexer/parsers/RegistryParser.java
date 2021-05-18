package dpf.sp.gpinf.indexer.parsers;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Set;

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

import dpf.sp.gpinf.indexer.parsers.util.Messages;
import dpf.sp.gpinf.indexer.parsers.util.Util;
import dpf.sp.gpinf.indexer.util.SimpleHTMLEncoder;

public class RegistryParser extends AbstractParser {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static Logger LOGGER = LoggerFactory.getLogger(RegistryParser.class);

    private static Set<MediaType> SUPPORTED_TYPES = null;
    private static String[] cmd;
    private static String TOOL_NAME = "rip"; //$NON-NLS-1$
    private static boolean tested = false;
    private static String[] regNames = { "sam", "software", "system", "security", "ntuser", "usrclass" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
    private static Charset charset = Charset.forName("UTF-8"); //$NON-NLS-1$

    private RawStringParser rawParser = new RawStringParser();

    public static final String TOOL_PATH_PROP = TOOL_NAME + ".path"; //$NON-NLS-1$
    private String TOOL_PATH = System.getProperty(TOOL_PATH_PROP, ""); //$NON-NLS-1$

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {

        if (SUPPORTED_TYPES != null)
            return SUPPORTED_TYPES;

        synchronized (this.getClass()) {
            if (!tested) {
                try {
                    cmd = new String[] { TOOL_PATH + TOOL_NAME };
                    if (!System.getProperty("os.name").toLowerCase().startsWith("windows")) //$NON-NLS-1$ //$NON-NLS-2$
                        cmd = new String[] { "perl", "-I", ".", TOOL_NAME + ".pl" }; //$NON-NLS-1$ //$NON-NLS-2$

                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    File dir = new File(TOOL_PATH);
                    if (dir.exists())
                        pb.directory(dir);
                    Process p = pb.start();
                    p.waitFor();
                    if (p.exitValue() != 0)
                        throw new Exception("Rip exit value = " + p.exitValue());

                    SUPPORTED_TYPES = Collections.singleton(MediaType.application("x-windows-registry")); //$NON-NLS-1$

                } catch (Exception e) {
                    StringBuilder sb = new StringBuilder();
                    for (String s : cmd)
                        sb.append(s).append(" ");
                    String msg = "Error testing rip (RegRipper): registry reports will NOT be created." //$NON-NLS-1$
                            + " Command line: " + sb.toString() //$NON-NLS-1$
                            + " Check if perl module 'Parse::Win32Registry' is installed."; //$NON-NLS-1$
                    // are we in analysis app?
                    if (System.getProperty("iped.javaVersionChecked") != null)
                        LOGGER.warn(msg);
                    else
                        LOGGER.error(msg);

                    SUPPORTED_TYPES = Collections.EMPTY_SET;
                }
                tested = true;
            }
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
        try {
            TikaInputStream tis = TikaInputStream.get(stream, tmp);

            String filename = metadata.get(Metadata.RESOURCE_NAME_KEY);
            File tempFile = null;
            String[] finalCmd = null;
            for (String regName : regNames)
                if (filename.toLowerCase().startsWith(regName)) {
                    tempFile = tis.getFile();
                    String[] params = new String[] { "-f", regName, "-r", tempFile.getAbsolutePath() }; //$NON-NLS-1$ //$NON-NLS-2$

                    finalCmd = new String[cmd.length + params.length];
                    for (int i = 0; i < cmd.length; i++)
                        finalCmd[i] = cmd[i];
                    for (int i = 0; i < params.length; i++)
                        finalCmd[cmd.length + i] = params[i];
                }

            // indexa strings brutas
            rawParser.parse(tis, handler, metadata, context);

            if (finalCmd != null) {
                ProcessBuilder pb = new ProcessBuilder(finalCmd);
                pb.directory(new File(TOOL_PATH));
                Process p = pb.start();

                readStream(p.getErrorStream(), null, null);

                File outFile = tmp.createTemporaryFile();
                OutputStream os = new FileOutputStream(outFile);
                try {
                    ContainerVolatile msg = new ContainerVolatile();
                    Thread thread = readStream(p.getInputStream(), os, msg);
                    waitFor(p, xhtml, msg);
                    // p.waitFor();
                    thread.join();

                } catch (InterruptedException e) {
                    p.destroyForcibly();
                    throw new TikaException(this.getClass().getSimpleName() + " interrupted", e); //$NON-NLS-1$

                } finally {
                    os.close();
                }

                Metadata reportMetadata = new Metadata();
                reportMetadata.set(Metadata.RESOURCE_NAME_KEY, filename + "-Report"); //$NON-NLS-1$
                reportMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "application/x-windows-registry-report"); //$NON-NLS-1$

                File htmlFile = getHtml(outFile, tmp);

                if (extractor.shouldParseEmbedded(reportMetadata))
                    try (InputStream is = new FileInputStream(htmlFile)) {
                        extractor.parseEmbedded(is, xhtml, reportMetadata, true);
                    }
            }

        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.warn("Error parsing " + metadata.get(Metadata.RESOURCE_NAME_KEY), e); //$NON-NLS-1$
        } finally {
            tmp.close();
        }

        xhtml.endDocument();

    }

    private File getHtml(File file, TemporaryResources tmp) throws IOException {
        File html = tmp.createTemporaryFile();
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(html), charset);) {
            writer.write("<html>"); //$NON-NLS-1$
            writer.write("<head>"); //$NON-NLS-1$
            writer.write("<meta http-equiv=\"content-type\" content=\"text/html; charset=" + charset + "\" />"); //$NON-NLS-1$ //$NON-NLS-2$
            writer.write("</head>"); //$NON-NLS-1$
            writer.write("<body>"); //$NON-NLS-1$
            writer.write("<pre>"); //$NON-NLS-1$

            String content = Util.decodeMixedCharset(Files.readAllBytes(file.toPath()));
            content = adjustDateFormat(content);
            writer.write(SimpleHTMLEncoder.htmlEncode(content.trim()));

            writer.write("</pre>"); //$NON-NLS-1$
            writer.write("</body>"); //$NON-NLS-1$
            writer.write("</html>"); //$NON-NLS-1$
        }

        return html;
    }

    private static String adjustDateFormat(String content) {
        final char[] dateFormat = "Aaa Aaa nN nN:NN:NN NNNN".toCharArray(); //$NON-NLS-1$
        final String[] srcWDay = new String[] { "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
        final String[] tgtWDay = new String[] { Messages.getString("RegistryParser.Sun"), //$NON-NLS-1$
                Messages.getString("RegistryParser.Mon"), Messages.getString("RegistryParser.Tue"), //$NON-NLS-1$ //$NON-NLS-2$
                Messages.getString("RegistryParser.Wed"), Messages.getString("RegistryParser.Thu"), //$NON-NLS-1$ //$NON-NLS-2$
                Messages.getString("RegistryParser.Fri"), Messages.getString("RegistryParser.Sat") }; //$NON-NLS-1$ //$NON-NLS-2$
        final String[] srcMonth = new String[] { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$
                "Nov", "Dec" }; //$NON-NLS-1$ //$NON-NLS-2$
        final String[] tgtMonth = new String[] { Messages.getString("RegistryParser.Jan"), //$NON-NLS-1$
                Messages.getString("RegistryParser.Feb"), Messages.getString("RegistryParser.Mar"), //$NON-NLS-1$ //$NON-NLS-2$
                Messages.getString("RegistryParser.Apr"), Messages.getString("RegistryParser.May"), //$NON-NLS-1$ //$NON-NLS-2$
                Messages.getString("RegistryParser.Jun"), Messages.getString("RegistryParser.Jul"), //$NON-NLS-1$ //$NON-NLS-2$
                Messages.getString("RegistryParser.Aug"), Messages.getString("RegistryParser.Sep"), //$NON-NLS-1$ //$NON-NLS-2$
                Messages.getString("RegistryParser.Oct"), Messages.getString("RegistryParser.Nov"), //$NON-NLS-1$ //$NON-NLS-2$
                Messages.getString("RegistryParser.Dec") }; //$NON-NLS-1$

        char[] c = content.toCharArray();
        boolean changed = false;
        StringBuilder sb = new StringBuilder();
        NEXT: for (int i = 0; i < c.length - dateFormat.length; i++) {
            for (int j = 0; j < dateFormat.length; j++) {
                char a = c[i + j];
                char b = dateFormat[j];
                if (b == 'A') {
                    if (!Character.isUpperCase(a))
                        continue NEXT;
                } else if (b == 'a') {
                    if (!Character.isLowerCase(a))
                        continue NEXT;
                } else if (b == 'N') {
                    if (!Character.isDigit(a))
                        continue NEXT;
                } else if (b == 'n') {
                    if (!Character.isDigit(a) && a != ' ')
                        continue NEXT;
                } else if (a != b) {
                    continue NEXT;
                }
            }
            String inWDay = new String(c, i, 3);
            String outWDay = null;
            for (int k = 0; k < srcWDay.length; k++) {
                if (inWDay.equals(srcWDay[k])) {
                    outWDay = tgtWDay[k];
                    break;
                }
            }
            if (outWDay == null)
                continue NEXT;
            String inMonth = new String(c, i + 4, 3);
            String outMonth = null;
            for (int k = 0; k < srcMonth.length; k++) {
                if (inMonth.equals(srcMonth[k])) {
                    outMonth = tgtMonth[k];
                    break;
                }
            }
            if (outMonth == null)
                continue NEXT;
            changed = true;
            sb.delete(0, sb.length());
            sb.append(outWDay).append(' ');
            sb.append(c, i + 8, 2).append('/');
            sb.append(outMonth).append('/');
            sb.append(c, i + 20, 4).append(' ');
            sb.append(c, i + 11, 8);
            sb.getChars(0, sb.length(), c, i);
            if (c[i + 4] == ' ')
                c[i + 4] = '0';
            if (c[i + 16] == ' ')
                c[i + 16] = '0';
            i += dateFormat.length - 1;
        }
        return changed ? new String(c) : content;
    }

    private Thread readStream(final InputStream stream, final OutputStream os, final ContainerVolatile msg) {
        Thread t = new Thread() {
            @Override
            public void run() {
                byte[] out = new byte[1024];
                int read = 0;
                while (read != -1)
                    try {
                        if (os != null)
                            os.write(out, 0, read);
                        if (msg != null)
                            msg.progress = true;
                        read = stream.read(out);

                    } catch (Exception e) {
                    }
            }
        };
        t.start();

        return t;
    }

    private void waitFor(Process p, ContentHandler handler, ContainerVolatile msg) throws InterruptedException {

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

    class ContainerVolatile {
        volatile boolean progress = false;
    }

}
