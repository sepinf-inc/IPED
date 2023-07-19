package iped.parsers.registry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.data.ICaseData;
import iped.parsers.registry.model.RegistryFileException;
import iped.parsers.standard.RawStringParser;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.ItemInfo;
import iped.properties.ExtraProperties;
import iped.utils.IOUtil;
import iped.utils.SimpleHTMLEncoder;

public class RegRipperParser extends AbstractParser {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final String FULL_REPORT_SUFFIX = "_Full_Report";

    private static Logger LOGGER = LoggerFactory.getLogger(RegRipperParser.class);

    private final static String timeBiasStartTag = "Bias           -&gt; ";
    private final static String timeBiasEndTag = " (";

    private static Set<MediaType> SUPPORTED_TYPES = null;
    private static String[] cmd;
    private static String TOOL_NAME = "rip"; //$NON-NLS-1$
    private static boolean tested = false;
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
                    if (!TOOL_PATH.isEmpty()) {
                        pb.directory(new File(TOOL_PATH));
                    }
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

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        
        TemporaryResources tmp = new TemporaryResources();
        try {
            TikaInputStream tis = TikaInputStream.get(stream, tmp);

            File tempFile = tis.getFile();

            // index raw strings (important because not all keys/values are extracted by regripper)
            rawParser.parse(tis, handler, metadata, context);
            
            String filename = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
            if (filename.matches(".*\\.LOG\\d?")) {
                // skip parsing log files
                return;
            }

            // run all available plugins for this hive
            ArrayList<String> command = new ArrayList<>(Arrays.asList(cmd));
            command.addAll(Arrays.asList("-a", "-r", tempFile.getAbsolutePath()));
            String reportName = filename + FULL_REPORT_SUFFIX;
            runCmdAndCreateReport(command, reportName, xhtml, extractor, tmp, metadata, context);

            // run specific profiles for each hive
            String regType = detectHive(tempFile);
            String profiles = "profiles/" + regType;
            File dir = new File(TOOL_PATH + "/plugins/" + profiles);
            File[] directoryListing = dir.listFiles();
            if (directoryListing != null) {
                for (File child : directoryListing) {
                    command = new ArrayList<>(Arrays.asList(cmd));
                    command.addAll(Arrays.asList("-f", profiles + "/" + child.getName(), "-r", tempFile.getAbsolutePath()));
                    
                    reportName = filename + "_" + child.getName().replace("_", "") + "_Report";
                    runCmdAndCreateReport(command, reportName, xhtml, extractor, tmp, metadata, context);
                }
            }
           
        } finally {
            xhtml.endDocument();
            tmp.close();
        }
    }

    private void extractCaseTimezone(String nome, String caminho, File htmlFile, ParseContext context) throws RegistryFileException, IOException {
        TimeZone tz = null;

        String content = Files.readString(htmlFile.toPath());

        int start = content.indexOf(timeBiasStartTag) + timeBiasStartTag.length();
        if (start > 0) {
            String timeBiasStr = content.substring(start, content.indexOf(timeBiasEndTag, start)).trim();
            int timeBias = Integer.parseInt(timeBiasStr);
            String[] tzs = TimeZone.getAvailableIDs(timeBias * 60 * 1000 * -1);
            if (tzs != null && tzs.length > 0) {
                for (String _tz : tzs) {
                    // prioritize local timezone if some equal ID was returned
                    if (_tz.equals(TimeZone.getDefault().getID())) {
                        tz = TimeZone.getTimeZone(_tz);
                        break;
                    }
                }
                if (tz == null) {
                    // fallback to first if not found
                    tz = TimeZone.getTimeZone(tzs[0]);
                }
            }

            ICaseData caseData = context.get(ICaseData.class);
            if (caseData != null) {
                synchronized (caseData) {
                    if (nome.equals("SYSTEM")) {
                        if (tz != null) {
                            HashMap<String, TimeZone> tzs2 = (HashMap<String, TimeZone>) caseData.getCaseObject(ICaseData.TIMEZONE_INFO_KEY);
                            if (tzs2 == null) {
                                tzs2 = new HashMap<String, TimeZone>();
                                caseData.addCaseObject(ICaseData.TIMEZONE_INFO_KEY, tzs2);
                            }
                            tzs2.put(caminho, tz);
                        }
                    }
                }
            }
        }
    }

    private void runCmdAndCreateReport(List<String> command, String reportName, ContentHandler handler, EmbeddedDocumentExtractor extractor, TemporaryResources tmp, Metadata metadata, ParseContext context) throws IOException, TikaException, SAXException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (!TOOL_PATH.isEmpty()) {
            pb.directory(new File(TOOL_PATH));
        }
        Process p = pb.start();

        readStream(p.getErrorStream(), null, null);

        File outFile = tmp.createTemporaryFile();
        OutputStream os = new FileOutputStream(outFile);
        try {
            ContainerVolatile msg = new ContainerVolatile();
            Thread thread = readStream(p.getInputStream(), os, msg);
            waitFor(p, handler, msg);
            // p.waitFor();
            thread.join();

        } catch (InterruptedException e) {
            p.destroyForcibly();
            throw new TikaException(this.getClass().getSimpleName() + " interrupted", e); //$NON-NLS-1$

        } finally {
            os.close();
        }

        File htmlFile = getHtml(outFile, tmp);
        if (htmlFile == null) {
            // ignores empty reports
            return;
        }
        
        Metadata reportMetadata = new Metadata();
        reportMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, reportName);
        reportMetadata.set(StandardParser.INDEXER_CONTENT_TYPE, "application/x-windows-registry-report"); //$NON-NLS-1$
        reportMetadata.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

        if (reportName.contains("SYSTEM_OS")) {
            String nome = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY).toUpperCase();
            ItemInfo itemInfo = context.get(ItemInfo.class);
            String caminho = itemInfo.getPath().toLowerCase().replace("\\", "/");
            try {
                extractCaseTimezone(nome, caminho, htmlFile, context);
            } catch (RegistryFileException | IOException e) {
                e.printStackTrace();
            }
        }

        if (extractor.shouldParseEmbedded(reportMetadata)) {
            try (InputStream is = new FileInputStream(htmlFile)) {
                extractor.parseEmbedded(is, handler, reportMetadata, true);
            }
        }
    }

    private String detectHive(File file) throws IOException {
        ArrayList<String> detectCmd = new ArrayList<>(Arrays.asList(cmd));
        detectCmd.addAll(Arrays.asList("-g", "-r", file.getAbsolutePath()));
        ProcessBuilder pb = new ProcessBuilder(detectCmd);
        if (!TOOL_PATH.isEmpty()) {
            pb.directory(new File(TOOL_PATH));
        }
        Process p = pb.start();
        IOUtil.ignoreErrorStream(p);
        byte[] bytes = IOUtil.loadInputStream(p.getInputStream());
        return new String(bytes, StandardCharsets.ISO_8859_1).strip();
    }

    private File getHtml(File file, TemporaryResources tmp) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()), "UTF-8");
        if (content == null || content.isBlank()) {
            return null;
        }
        File html = tmp.createTemporaryFile();
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(html), charset);) {
            writer.write("<html>"); //$NON-NLS-1$
            writer.write("<head>"); //$NON-NLS-1$
            writer.write("<meta http-equiv=\"content-type\" content=\"text/html; charset=" + charset + "\" />"); //$NON-NLS-1$ //$NON-NLS-2$
            writer.write("</head>"); //$NON-NLS-1$
            writer.write("<body>"); //$NON-NLS-1$
            writer.write("<pre>"); //$NON-NLS-1$
            writer.write(SimpleHTMLEncoder.htmlEncode(content.strip()));
            writer.write("</pre>"); //$NON-NLS-1$
            writer.write("</body>"); //$NON-NLS-1$
            writer.write("</html>"); //$NON-NLS-1$
        }

        return html;
    }

    private Thread readStream(final InputStream stream, final OutputStream os, final ContainerVolatile msg) {
        Thread t = new Thread() {
            @Override
            public void run() {
                byte[] out = new byte[1024];
                int read = 0;
                try {
                    while (read != -1) {
                        if (os != null)
                            os.write(out, 0, read);
                        if (msg != null)
                            msg.progress = true;
                        read = stream.read(out);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
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
