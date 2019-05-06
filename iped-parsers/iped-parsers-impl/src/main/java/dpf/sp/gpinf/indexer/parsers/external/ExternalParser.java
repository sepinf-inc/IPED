/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dpf.sp.gpinf.indexer.parsers.external;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.NullOutputStream;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.HtmlMapper;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.parser.html.IdentityHtmlMapper;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.util.IOUtil;

/**
 * Parser that uses an external program (like catdoc or pdf2txt) to extract
 * text content and metadata from a given document.
 */
public class ExternalParser extends AbstractParser {
    
    private static Logger LOGGER;
    
    public static final String EXTERNAL_PARSERS_ROOT = "iped.extParsers.root";

    /**
     * Consumer contract
     *
     * @since Apache Tika 1.14
     */
    public interface LineConsumer extends Serializable {
        /**
         * Consume a line
         *
         * @param line a line of string
         */
        void consume(String line);

        /**
         * A null consumer
         */
        LineConsumer NULL = new LineConsumer() {
            @Override
            public void consume(String line) {
                // ignores
            }
        };
    }

    private static final long serialVersionUID = -1079128990650687037L;

    /**
     * The token, which if present in the Command string, will
     * be replaced with the input filename.
     * Alternately, the input data can be streamed over STDIN.
     */
    public static final String INPUT_FILE_TOKEN = "${INPUT}";
    /**
     * The token, which if present in the Command string, will
     * be replaced with the output filename.
     * Alternately, the output data can be collected on STDOUT.
     */
    public static final String OUTPUT_FILE_TOKEN = "${OUTPUT}";

    /**
     * Media types supported by the external program.
     */
    private Set<MediaType> supportedTypes = Collections.emptySet();

    /**
     * Regular Expressions to run over STDOUT to
     * extract Metadata.
     */
    private Map<Pattern, String> metadataPatterns = null;

    /**
     * The external command to invoke.
     *
     * @see Runtime#exec(String[])
     */
    private String[] command = new String[]{"cat"};

    /**
     * A consumer for ignored Lines
     */
    private LineConsumer ignoredLineConsumer = LineConsumer.NULL;
    
    /**
     * Name of external parser
     */
    private String parserName;
    
    private String toolPath;
    
    private static String rootFolder;
    
    private String charset = "UTF-8";
    
    private boolean outputHtml = false;
    
    private int linesToIgnore = 0;
    
    private HtmlParser htmlParser = new HtmlParser();

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return getSupportedTypes();
    }

    public Set<MediaType> getSupportedTypes() {
        return supportedTypes;
    }

    public void setSupportedTypes(Set<MediaType> supportedTypes) {
        this.supportedTypes =
                Collections.unmodifiableSet(new HashSet<MediaType>(supportedTypes));
    }


    public String[] getCommand() {
        return command;
    }

    /**
     * Sets the command to be run. This can include either of
     * {@link #INPUT_FILE_TOKEN} or {@link #OUTPUT_FILE_TOKEN}
     * if the command needs filenames.
     *
     * @see Runtime#exec(String[])
     */
    public void setCommand(String... command) {
        this.command = command;
    }

    /**
     * Gets lines consumer
     *
     * @return consumer instance
     */
    public LineConsumer getIgnoredLineConsumer() {
        return ignoredLineConsumer;
    }

    /**
     * Set a consumer for the lines ignored by the parse functions
     *
     * @param ignoredLineConsumer consumer instance
     */
    public void setIgnoredLineConsumer(LineConsumer ignoredLineConsumer) {
        this.ignoredLineConsumer = ignoredLineConsumer;
    }

    public Map<Pattern, String> getMetadataExtractionPatterns() {
        return metadataPatterns;
    }

    /**
     * Sets the map of regular expression patterns and Metadata
     * keys. Any matching patterns will have the matching
     * metadata entries set.
     * Set this to null to disable Metadata extraction.
     */
    public void setMetadataExtractionPatterns(Map<Pattern, String> patterns) {
        this.metadataPatterns = patterns;
    }


    /**
     * Executes the configured external command and passes the given document
     * stream as a simple XHTML document to the given SAX content handler.
     * Metadata is only extracted if {@link #setMetadataExtractionPatterns(Map)}
     * has been called to set patterns.
     */
    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        XHTMLContentHandler xhtml =
                new XHTMLContentHandler(handler, metadata);
        
        if(LOGGER == null)
            LOGGER = LoggerFactory.getLogger(ExternalParser.class);

        TemporaryResources tmp = new TemporaryResources();
        try {
            parse(TikaInputStream.get(stream, tmp),
                    xhtml, metadata, tmp);
        } finally {
            tmp.dispose();
        }
    }
    
    public static String getRootFolder() {
        if(rootFolder != null)
            return rootFolder;
        
        rootFolder = System.getProperty(EXTERNAL_PARSERS_ROOT);
        if(rootFolder != null) return rootFolder;
        
        URL url = ExternalParser.class.getProtectionDomain().getCodeSource().getLocation();
        try {
            rootFolder = new File(url.toURI()).getParentFile().getParentFile().getAbsolutePath();
            
        } catch (URISyntaxException e) {
            e.printStackTrace();
            rootFolder = "";
        }
        return rootFolder;
    }

    private void parse(
            TikaInputStream stream, XHTMLContentHandler xhtml,
            Metadata metadata, TemporaryResources tmp)
            throws IOException, SAXException, TikaException {
        boolean inputToStdIn = true;
        boolean outputFromStdOut = true;
        boolean hasPatterns = (metadataPatterns != null && !metadataPatterns.isEmpty());

        File outputFile = tmp.createTemporaryFile();

        // Build our command
        String[] cmd;
        if (command.length == 1) {
            cmd = command[0].split(" ");
        } else {
            cmd = new String[command.length];
            System.arraycopy(command, 0, cmd, 0, command.length);
        }
        for (int i = 0; i < cmd.length; i++) {
            if (cmd[i].indexOf(INPUT_FILE_TOKEN) != -1) {
                cmd[i] = cmd[i].replace(INPUT_FILE_TOKEN, stream.getFile().getPath());
                inputToStdIn = false;
            }
            if (cmd[i].indexOf(OUTPUT_FILE_TOKEN) != -1) {
                outputFromStdOut = false;
                cmd[i] = cmd[i].replace(OUTPUT_FILE_TOKEN, outputFile.getPath());
                outputFile.delete();
            }
        }
        if(SystemUtils.IS_OS_WINDOWS && toolPath != null) {
            String fullPath = getRootFolder() + "/" + toolPath;
            cmd[0] = fullPath + cmd[0]; 
        }
        // Execute
        Process process = null;
        try {
            if (cmd.length == 1) {
                process = Runtime.getRuntime().exec(cmd[0]);
            } else {
                process = Runtime.getRuntime().exec(cmd);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        try {
            if (inputToStdIn) {
                sendInput(process, stream);
            } else {
                process.getOutputStream().close();
            }
            
            InputStream out = process.getInputStream();
            InputStream err = process.getErrorStream();

            if (hasPatterns) {
                //some tools output info to stderr (eg ffmpeg)
                Thread t1 = extractMetadataInBackground(err, metadata);
                
                if (outputFromStdOut) {
                    Thread t  = extractOutputInBackground(out, outputFile);
                    t.join();
                } else {
                    ignoreStream(out);
                }
                t1.join();
                
            } else {
                ignoreStream(err);

                if (outputFromStdOut) {
                    Thread t  = extractOutputInBackground(out, outputFile);
                    t.join();
                } else {
                    ignoreStream(out);
                }
            }
            
            process.waitFor();
            
            try(InputStream is = new FileInputStream(outputFile)){
                if (hasPatterns) {
                    extractMetadata(is, metadata);
                }else {
                    extractOutput(is, xhtml);
                }
            }
            
        } catch (InterruptedException e) {
            LOGGER.warn(parserName + " interrupted while processing " + metadata.get(Metadata.RESOURCE_NAME_KEY)
            + " (" + metadata.get(Metadata.CONTENT_LENGTH) + " bytes)");
            
            if(process != null)
                process.destroyForcibly();
            
            throw new TikaException(this.getParserName() + " interrupted.", e);
            
        }finally {
            IOUtil.closeQuietly(tmp);
        }

    }
    
    private Thread extractOutputInBackground(final InputStream stream, final File outFile) {
        Thread t = new Thread() {
            public void run() {
                try {
                    Files.copy(stream, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    
                } catch (IOException e) {}
            }
        };
        t.start();
        return t;
    }

    /**
     * Starts a thread that extracts the contents of the standard output
     * stream of the given process to the given XHTML content handler.
     * The standard output stream is closed once fully processed.
     *
     * @param process process
     * @param xhtml   XHTML content handler
     * @throws SAXException if the XHTML SAX events could not be handled
     * @throws IOException  if an input error occurred
     * @throws TikaException 
     */
    private void extractOutput(InputStream stream, XHTMLContentHandler xhtml)
            throws SAXException, IOException, TikaException {
        if(outputHtml) {
            ParseContext context = new ParseContext();
            context.set(HtmlMapper.class, IdentityHtmlMapper.INSTANCE);
            htmlParser.parse(stream, xhtml, new Metadata(), context);
            return;
        }
        try (Reader reader = new InputStreamReader(stream, charset)) {
            xhtml.startDocument();
            xhtml.startElement("p");
            char[] buffer = new char[1024];
            int line = 1;
            for (int n = reader.read(buffer); n != -1; n = reader.read(buffer)) {
                int prev = 0;
                for(int i = 0; i < n; i++) {
                    if(buffer[i] == '\n') {
                        if(line++ > linesToIgnore) {
                            xhtml.characters(buffer, prev, i - prev);
                            xhtml.startElement("br");
                            xhtml.endElement("br");
                        }
                        prev = i + 1;
                    }
                }
                if(line > linesToIgnore) {
                    xhtml.characters(buffer, prev, n - prev);
                }
            }
            xhtml.endElement("p");
            xhtml.endDocument();
        }
    }

    /**
     * Starts a thread that sends the contents of the given input stream
     * to the standard input stream of the given process. Potential
     * exceptions are ignored, and the standard input stream is closed
     * once fully processed. Note that the given input stream is <em>not</em>
     * closed by this method.
     *
     * @param process process
     * @param stream  input stream
     */
    private void sendInput(final Process process, final InputStream stream) {
        Thread t = new Thread() {
            public void run() {
                OutputStream stdin = process.getOutputStream();
                try {
                    IOUtils.copy(stream, stdin);
                } catch (IOException e) {
                }
            }
        };
        t.start();
    }


    /**
     * Starts a thread that reads and discards the contents of the
     * standard stream of the given process. Potential exceptions
     * are ignored, and the stream is closed once fully processed.
     * Note: calling this starts a new thread and blocks the current(caller) thread until the new thread dies
     *
     * @param stream stream to be ignored
     */
    private static void ignoreStream(final InputStream stream) {
        ignoreStream(stream, false);
    }

    /**
     * Starts a thread that reads and discards the contents of the
     * standard stream of the given process. Potential exceptions
     * are ignored, and the stream is closed once fully processed.
     *
     * @param stream       stream to sent to black hole (a k a null)
     * @param waitForDeath when {@code true} the caller thread will be blocked till the death of new thread.
     * @return The thread that is created and started
     */
    private static Thread ignoreStream(final InputStream stream, boolean waitForDeath) {
        Thread t = new Thread() {
            public void run() {
                try {
                    IOUtils.copy(stream, new NullOutputStream());
                } catch (IOException e) {
                } finally {
                    IOUtils.closeQuietly(stream);
                }
            }
        };
        t.start();
        if (waitForDeath) {
            try {
                t.join();
            } catch (InterruptedException ignore) {
            }
        }
        return t;
    }
    
    private void extractMetadata(final InputStream stream, final Metadata metadata) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, charset))){
            String line;
            while ((line = reader.readLine()) != null) {
                boolean consumed = false;
                for (Pattern p : metadataPatterns.keySet()) {
                    Matcher m = p.matcher(line);
                    if (m.find()) {
                        consumed = true;
                        if (metadataPatterns.get(p) != null &&
                                !metadataPatterns.get(p).equals("")) {
                            metadata.add(metadataPatterns.get(p), m.group(1));
                        } else {
                            metadata.add(m.group(1), m.group(2));
                        }
                    }
                }
                if (!consumed) {
                    ignoredLineConsumer.consume(line);
                }
            }
        } catch (IOException e) {
            // Ignore
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

    private Thread extractMetadataInBackground(final InputStream stream, final Metadata metadata) throws TikaException {
        Thread t = new Thread() {
            public void run() {
                extractMetadata(stream, metadata);
            }
        };
        t.start();
        return t;
    }

    /**
     * Checks to see if the command can be run. Typically used with
     * something like "myapp --version" to check to see if "myapp"
     * is installed and on the path.
     *
     * @param checkCmd   The check command to run
     * @param errorValue What is considered an error value?
     */
    public static boolean check(String checkCmd, int... errorValue) {
        return check(new String[]{checkCmd}, errorValue);
    }

    public static boolean check(String[] checkCmd, int... errorValue) {
        if (errorValue.length == 0) {
            errorValue = new int[]{127};
        }

        try {
            Process process = Runtime.getRuntime().exec(checkCmd);
            Thread stdErrSuckerThread = ignoreStream(process.getErrorStream(), false);
            Thread stdOutSuckerThread = ignoreStream(process.getInputStream(), false);
            stdErrSuckerThread.join();
            stdOutSuckerThread.join();
            int result = process.waitFor();
            for (int err : errorValue) {
                if (result == err) return false;
            }
            return true;
        } catch (IOException e) {
            // Some problem, command is there or is broken
            return false;
        } catch (InterruptedException ie) {
            // Some problem, command is there or is broken
            return false;
        } catch (SecurityException se) {
            // External process execution is banned by the security manager
            return false;
        } catch (Error err) {
            if (err.getMessage() != null &&
                    (err.getMessage().contains("posix_spawn") ||
                            err.getMessage().contains("UNIXProcess"))) {
                //"Error forking command due to JVM locale bug
                //(see TIKA-1526 and SOLR-6387)"
                return false;
            }
            //throw if a different kind of error
            throw err;
        }
    }

    public String getParserName() {
        return parserName;
    }

    public void setParserName(String parserName) {
        this.parserName = parserName;
    }

    public String getToolPath() {
        return toolPath;
    }

    public void setToolPath(String toolPath) {
        this.toolPath = toolPath;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public boolean isOutputHtml() {
        return outputHtml;
    }

    public void setOutputHtml(boolean outputHtml) {
        this.outputHtml = outputHtml;
    }

    public int getLinesToIgnore() {
        return linesToIgnore;
    }

    public void setLinesToIgnore(int linesToIgnore) {
        this.linesToIgnore = linesToIgnore;
    }
}
