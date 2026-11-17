package iped.parsers.registry;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
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

import com.google.common.io.LineReader;

import iped.data.ICaseData;
import iped.parsers.registry.model.RegistryFileException;
import iped.parsers.standard.RawStringParser;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.ItemInfo;
import iped.parsers.util.MetadataUtil;
import iped.properties.ExtraProperties;
import iped.utils.DateUtil;
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

    private static final String WINREG_PREFIX = "WinReg:";

    private String TOOL_PATH = System.getProperty(TOOL_PATH_PROP, ""); //$NON-NLS-1$

    private boolean extractTimestampViaTLNPlugins = false;
    
    ArrayList<String> fileListPlugins = new ArrayList<String>(Arrays.asList("arpcache", "bam", "appcompatcache", "shimcache", "jumplistdata", "appcompatflags"));

    private boolean extractTimestamps = true;

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

    @Field
    public void setExtractTimestamps(boolean extractTimestamps) {
        this.extractTimestamps = extractTimestamps;
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

            if(extractTimestampViaTLNPlugins && extractTimestamps) {
                command = new ArrayList<>(Arrays.asList(cmd));
                command.addAll(Arrays.asList("-aT", "-r", tempFile.getAbsolutePath()));
                runCmdAndExtractTimeline(command, reportName, xhtml, extractor, tmp, metadata, context);
            }

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


    private File runCmd(List<String> command, String reportName, ContentHandler handler,
            EmbeddedDocumentExtractor extractor, TemporaryResources tmp, Metadata metadata, ParseContext context) throws IOException, TikaException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (!TOOL_PATH.isEmpty()) {
            pb.directory(new File(TOOL_PATH));
        }
        Process p = pb.start();

        readStream(p.getErrorStream(), null, null, null, null, null);

        File outFile = tmp.createTemporaryFile();
        OutputStream os = new FileOutputStream(outFile);
        try {
            ContainerVolatile msg = new ContainerVolatile();
            Thread thread = readStream(p.getInputStream(), os, msg, metadata, handler, extractor, !extractTimestampViaTLNPlugins && !command.contains("-f"));
            waitFor(p, handler, msg);
            thread.join();

        } catch (InterruptedException e) {
            p.destroyForcibly();
            throw new TikaException(this.getClass().getSimpleName() + " interrupted", e); //$NON-NLS-1$

        } finally {
            os.close();
        }
        
        return outFile;
    }

    private void runCmdAndExtractTimeline(ArrayList<String> command, String reportName, XHTMLContentHandler handler,
            EmbeddedDocumentExtractor extractor, TemporaryResources tmp, Metadata metadata, ParseContext context) throws IOException, TikaException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (!TOOL_PATH.isEmpty()) {
            pb.directory(new File(TOOL_PATH));
        }
        Process p = pb.start();

        try {
            ContainerVolatile msg = new ContainerVolatile();
            Thread thread = extractTimestampsFromStream(p.getInputStream(), handler, extractor, msg, metadata);
            waitFor(p, handler, msg);
            thread.join();

        } catch (InterruptedException e) {
            p.destroyForcibly();
            throw new TikaException(this.getClass().getSimpleName() + " interrupted", e); //$NON-NLS-1$

        } 
    }

    private void runCmdAndCreateReport(List<String> command, String reportName, ContentHandler handler, EmbeddedDocumentExtractor extractor, TemporaryResources tmp, Metadata metadata, ParseContext context) throws IOException, TikaException, SAXException {
        File outFile = runCmd(command, reportName, handler, extractor, tmp, metadata, context);
        
        Metadata reportMetadata = new Metadata();
        File htmlFile = createReport(reportName, outFile, tmp, handler, context, extractor, reportMetadata);
        
        if(htmlFile!=null) {
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
        }
        
        

    }

    private File createReport(String reportName, File outFile, TemporaryResources tmp, ContentHandler handler, ParseContext context, EmbeddedDocumentExtractor extractor, Metadata reportMetadata) throws IOException, SAXException {
        File htmlFile = getHtml(outFile, tmp);
        if (htmlFile == null) {
            // ignores empty reports
            return null;
        }

        reportMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, reportName);
        reportMetadata.set(StandardParser.INDEXER_CONTENT_TYPE, "application/x-windows-registry-report"); //$NON-NLS-1$
        reportMetadata.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

        if (extractor.shouldParseEmbedded(reportMetadata)) {
            try (InputStream is = new FileInputStream(htmlFile)) {
                extractor.parseEmbedded(is, handler, reportMetadata, true);
            }
        }
        return htmlFile;
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
    
    class Plugin{
        StringBuffer name = new StringBuffer();
        StringBuffer description = new StringBuffer();
    }
    
    
    public String extractTimeMetadata(Metadata metadata, byte[] out, String remain, ContentHandler handler, EmbeddedDocumentExtractor extractor, Plugin lastPlugin) {
        try {
            int virtualid=100000;
            Pattern p = DateUtil.getDateStrPattern();
            String outStr = remain + new String(out, StandardCharsets.ISO_8859_1);
            String[] buff = outStr.split("\n");
            String value = null;
            int i = 0;
            int lastMatch=-1;
            for (; i < buff.length - 1; i++) {
                value = buff[i];
                int len = value.trim().length();
                if(len>0 && value.trim().equals("-".repeat(len))) {
                    lastPlugin.name.replace(0, lastPlugin.name.length(), "");
                    lastPlugin.description.replace(0, lastPlugin.description.length(), "");
                    
                    continue;
                }
                if(lastPlugin.name.length()==0) {
                    int si=value.indexOf(" ");
                    if(si==-1) {
                        lastPlugin.name.append(value);
                    }else {
                        int start=0;
                        if(value.startsWith("Launching")) {
                            start = si+1;
                            si=value.indexOf(" ", start);
                        }
                        lastPlugin.name.append(value.substring(start,si));
                    }
                    if(lastPlugin.name.toString().startsWith("xplorer_cu")) {
                        System.out.println();
                    }
                    continue;
                }
                if(lastPlugin.description.length()==0) {
                    if(value.equals("")) {
                        lastPlugin.description.append(" ");
                    }else {
                        lastPlugin.description.append(value);
                    }
                    continue;
                }
                String msofficeapp = isMSOfficePlugin(value, lastPlugin);
                if(msofficeapp!=null) {
                    lastPlugin.name.replace(0, lastPlugin.name.length(), "");
                    lastPlugin.description.replace(0, lastPlugin.description.length(), "");
                    int ind = value.indexOf("-");
                    if(ind != -1) {
                        lastPlugin.name.append("msoffice"+msofficeapp+value.substring(ind+1).trim());
                        lastPlugin.description.append(value);
                    }
                }
                Matcher m = p.matcher(value);
                lastMatch=-1;
                while(m.find()) {
                    String dateStr = value.substring(m.start(), m.end());
                    if(Character.isAlphabetic(dateStr.charAt(0))) {
                        dateStr = toIso(dateStr);
                    }
                    if(dateStr.length()<=19) {//if there is no timezone info considers UTC
                        dateStr+="Z";
                    }
                    if(!dateStr.startsWith("1970")){//if year is 1970, probably the date value contains only a time duration, not a timestamp
                        lastMatch=m.end();

                        String fieldName = extractFieldName(lastPlugin, m, value);
                        String content=lastPlugin.description.toString()+"\n"+value;
                        if(fileListPlugins.contains(lastPlugin.name.toString())) {
                            virtualid = tlnParser(i, WINREG_PREFIX+lastPlugin.name.toString(), dateStr, content, handler, metadata, extractor, virtualid);
                        }else {
                            fieldName = fieldName.replaceAll(" ", "");

                            virtualid = tlnParser(i, WINREG_PREFIX+lastPlugin.name.toString()+":"+fieldName, dateStr, content, handler, metadata, extractor, virtualid);
                        }
                    }
                }
            }
            if(buff.length>0) {
                value = buff[buff.length-1];
                if(outStr.endsWith("\n")) {
                    value+="\n";
                }
            }
            return value;
        }catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private String isMSOfficePlugin(String value, Plugin lastPlugin) {
        String name = lastPlugin.name.toString();
        if(!name.startsWith("msoffice")) {
            return null;
        }
        if(value.startsWith("Word")) {
            return "Word";
        }
        if(value.startsWith("Excel")) {
            return "Excel";
        }
        if(value.startsWith("PowerPoint")) {
            return "PowerPoint";
        }
        if(value.startsWith("Access")) {
            return "Access";
        }
        return null;
    }

    static String[] shellBagsFieldNames = {"Modified","Accessed","Created"};

    private String extractFieldName(Plugin lastPlugin, Matcher m, String value) {
        String fieldName="";

        if(lastPlugin.name.toString().startsWith("msoffice")) {
            return "EntryModified";
        }else if(lastPlugin.name.toString().equals("cached")) {
            return "ShellExtensionFirstLoad";
        }else if(lastPlugin.name.toString().equals("typedurlstime")) {
            return "UrlTyped";
        }if(lastPlugin.name.toString().equals("teamviewer")) {
            return "LastUpdateCheck";
        }else if(lastPlugin.name.toString().startsWith("shellbags") || lastPlugin.name.toString().equals("itempos")) {
            int count = StringUtils.countMatches(value.substring(0,m.start()), "|")-1;
            if(count>=shellBagsFieldNames.length) {
                return "unknown";
            }
            return shellBagsFieldNames[count];
        }else {
            String[] fieldNames = value.substring(0,m.start()).split(":");

            if(fieldNames[fieldNames.length-1].trim().equals("")) {//if the last subitem of split by ':' is blank
                if(fieldNames.length>=2) {
                    fieldName=fieldNames[fieldNames.length-2];//gets the second last
                }
            }else {
                fieldName=fieldNames[fieldNames.length-1];
            }
            fieldName=fieldName.trim();
            if(fieldName.length()==0) {
                //tries to extract field name from end of line
                if(value.length()>m.end()+3) {
                    fieldNames = value.substring(m.end()+1).split(":");
                    if(fieldNames.length>0) {
                        fieldName = fieldNames[0];
                    }
                }
            }
        }

        return fieldName;
    }

    private String toIso(String dateStr) {
        String month = dateStr.substring(4,7);
        String monthN=null;
        if(month.charAt(0)=='J') {
            if(month.charAt(1)=='a') {
                monthN="01";            
            }
            if(month.charAt(2)=='n') {
                monthN="06";            
            }
            if(month.charAt(2)=='l') {
                monthN="07";            
            }
        }
        if(month.charAt(0)=='F') {
            monthN="02";            
        }
        if(month.charAt(0)=='M') {
            if(month.charAt(2)=='r') {
                monthN="03";            
            }
            if(month.charAt(2)=='y') {
                monthN="05";            
            }
        }
        if(month.charAt(0)=='A') {
            if(month.charAt(2)=='g') {
                monthN="06";
            }
            if(month.charAt(1)=='p') {
                monthN="04";
            }
        }
        if(month.charAt(0)=='S') {
            monthN="09";            
        }
        if(month.charAt(0)=='O') {
            monthN="10";            
        }
        if(month.charAt(0)=='N') {
            monthN="11";            
        }
        if(month.charAt(0)=='D') {
            monthN="12";            
        }
        String day = dateStr.substring(7,10).trim();
        if(day.length()==1) {
            day = "0"+day;
        }
        String hour = dateStr.substring(11,19);
        String year = dateStr.substring(20,24);
        return year+"-"+monthN+"-"+day+" "+hour;
    }

    private int tlnParser(int parentId, String fieldName, String dateStr, String lineContent, ContentHandler handler, Metadata metadata, EmbeddedDocumentExtractor extractor, int virtualId) throws TikaException {
        if (extractor.shouldParseEmbedded(metadata)) {
            try {
                String titletimeEvent = fieldName.trim();
                if(titletimeEvent.endsWith("=")) {
                    titletimeEvent = titletimeEvent.substring(0,titletimeEvent.length()-1);
                }
                String fieldTimeEvent = removesUnwantedChars(fieldName);
                
                ByteArrayInputStream featureStream = new ByteArrayInputStream(lineContent.getBytes());

                Metadata kmeta = new Metadata();
                kmeta.set(HttpHeaders.CONTENT_TYPE, "text/plain");
                kmeta.set(TikaCoreProperties.TITLE, titletimeEvent);
                MetadataUtil.setMetadataType(fieldTimeEvent, Date.class);
                kmeta.set(fieldTimeEvent, dateStr);
                int id = ++virtualId;
                kmeta.set(ExtraProperties.ITEM_VIRTUAL_ID, Integer.toString(id));
                kmeta.set(ExtraProperties.PARENT_VIRTUAL_ID, Integer.toString(parentId));
                kmeta.set(StandardParser.INDEXER_CONTENT_TYPE, "application/x-windows-registry-report-timestamp"); //$NON-NLS-1$
                
                extractor.parseEmbedded(featureStream, handler, kmeta, false);

                return id;

            } catch (Exception e) {
                throw new TikaException(e.getMessage(), e);
            }
        }
        return -1;
    }
    
    private String removesUnwantedChars(String fieldName) {
        return  fieldName.trim().replace(" ", "").replace(".", "").replace("(", "_").replace(")", "").replace("=", "");
    }

    private int tlnParser(int parentId, String tlnLine, ContentHandler handler, Metadata metadata, EmbeddedDocumentExtractor extractor, int virtualId) throws TikaException {
        if (extractor.shouldParseEmbedded(metadata)) {
            try {
                String[] fields = tlnLine.split("\\|");
                String dateStr = fields[0];
                dateStr = DateUtil.dateToString(new Date(Long.parseLong(dateStr)*1000));
                String titletimeEvent = fields[fields.length-1].split("-")[0];
                String fieldTimeEvent = WINREG_PREFIX+removesUnwantedChars(titletimeEvent);

                ByteArrayInputStream featureStream = new ByteArrayInputStream(tlnLine.getBytes());

                Metadata kmeta = new Metadata();
                kmeta.set(HttpHeaders.CONTENT_TYPE, "text/plain");
                kmeta.set(TikaCoreProperties.TITLE, titletimeEvent);
                kmeta.set(fieldTimeEvent, dateStr);
                int id = ++virtualId;
                kmeta.set(ExtraProperties.ITEM_VIRTUAL_ID, Integer.toString(id));
                kmeta.set(ExtraProperties.PARENT_VIRTUAL_ID, Integer.toString(parentId));
                kmeta.set(StandardParser.INDEXER_CONTENT_TYPE, "application/x-windows-registry-report-timestamp"); //$NON-NLS-1$

                extractor.parseEmbedded(featureStream, handler, kmeta, false);

                return id;

            } catch (Exception e) {
                throw new TikaException(e.getMessage(), e);
            }
        }
        return -1;
    }
    

    private Thread readStream(final InputStream stream, final OutputStream os, final ContainerVolatile msg, Metadata metadata,ContentHandler handler, EmbeddedDocumentExtractor extractor) {
        return readStream(stream, os, msg, metadata, handler, extractor, !extractTimestampViaTLNPlugins );
    }

    private Thread readStream(final InputStream stream, final OutputStream os, final ContainerVolatile msg, Metadata metadata,ContentHandler handler, EmbeddedDocumentExtractor extractor, boolean extractTimestampParam) {
        final boolean extractTimestamp = extractTimestampParam && extractTimestamps;
        Thread t = new Thread() {
            @Override
            public void run() {
                byte[] out = new byte[1024];
                Plugin lastPlugin = new Plugin();
                int read = 0;
                String remain="";
                try {
                    while (read != -1) {
                        if (os != null) {
                            os.write(out, 0, read);
                            if(extractTimestamp && read>0) {
                                if(read==1024) {
                                    remain = extractTimeMetadata(metadata, out, remain, handler, extractor,lastPlugin);
                                }else {
                                    remain = extractTimeMetadata(metadata, Arrays.copyOfRange(out, 0, read-1), remain, handler, extractor,lastPlugin);
                                }
                            }
                        }
                        if (msg != null)
                            msg.progress = true;
                        read = stream.read(out);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if(extractTimestamp && !remain.equals("\n")) {//last line processing
                    remain+="\n\n";
                    out = new byte[0];
                    remain = extractTimeMetadata(metadata, out, remain, handler, extractor,lastPlugin);
                }
           }
        };
        t.start();

        return t;
    }

    private Thread extractTimestampsFromStream(final InputStream stream, ContentHandler handler, EmbeddedDocumentExtractor extractor, final ContainerVolatile msg, Metadata metadata) {
        Thread t = new Thread() {
            @Override
            public void run() {
                LineReader lr = new LineReader(new InputStreamReader(stream));
                String line;
                int virtualId=0;
                try {
                    line = lr.readLine();
                    LINELOOP:while(line!=null) {
                        int i=0;
                        if(line.length()<8) {
                            line = lr.readLine();
                            continue;
                        }
                        for(i=0; i<8 && i<line.length(); i++) {
                            if(!Character.isDigit(line.charAt(i))){
                                line = lr.readLine();
                                continue LINELOOP;
                            }
                        }
                        virtualId=tlnParser(-1, line, handler, metadata, extractor, virtualId);

                        line = lr.readLine();
                    }
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                System.out.println("timeEvent");
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