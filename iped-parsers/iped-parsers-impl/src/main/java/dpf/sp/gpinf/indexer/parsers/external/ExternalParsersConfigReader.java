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

import javax.xml.parsers.DocumentBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.apache.tika.exception.TikaException;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.utils.SystemUtils;
import org.apache.tika.utils.XMLReaderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Builds up ExternalParser instances based on XML file(s) which define what to
 * run, for what, and how to process any output metadata. Typically used to
 * configure up a series of external programs (like catdoc or pdf2txt) to
 * extract text content from documents.
 * 
 * <pre>
 *  TODO XML DTD Here
 * </pre>
 */
public final class ExternalParsersConfigReader implements ExternalParsersConfigReaderMetKeys {

    private static Logger LOGGER = LoggerFactory.getLogger(ExternalParsersConfigReader.class);

    private static Map<String, Boolean> cmdCheckResultCache = new ConcurrentHashMap<>();

    private static final Object lock = new Object();

    public static List<ExternalParser> read(InputStream stream) throws TikaException, IOException {
        try {
            DocumentBuilder builder = XMLReaderUtils.getDocumentBuilder();
            Document document = builder.parse(new InputSource(stream));
            return read(document);
        } catch (SAXException e) {
            throw new TikaException("Invalid parser configuration", e);
        }
    }

    public static List<ExternalParser> read(Document document) throws TikaException, IOException {
        return read(document.getDocumentElement());
    }

    public static List<ExternalParser> read(Element element) throws TikaException, IOException {
        List<ExternalParser> parsers = new ArrayList<ExternalParser>();

        if (element != null && element.getTagName().equals(EXTERNAL_PARSERS_TAG)) {
            NodeList nodes = element.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element child = (Element) node;
                    if (child.getTagName().equals(PARSER_TAG)) {
                        ExternalParser p = readParser(child);
                        if (p != null) {
                            parsers.add(p);
                        }
                    }
                }
            }
        } else {
            throw new MimeTypeException(
                    "Not a <" + EXTERNAL_PARSERS_TAG + "/> configuration document: " + element.getTagName());
        }

        return parsers;
    }

    /**
     * Builds and Returns an ExternalParser, or null if a check command was given
     * that didn't match.
     */
    private static ExternalParser readParser(Element parserDef) throws TikaException {
        ExternalParser parser = new ExternalParser();

        NodeList children = parserDef.getChildNodes();
        Element checkElement = null;
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                if (child.getTagName().equals(CHECK_TAG)) {
                    checkElement = child;
                } else if (child.getTagName().equals(COMMAND_TAG)) {
                    parser.setCommand(getString(child));
                } else if (child.getTagName().equals(MIMETYPES_TAG)) {
                    parser.setSupportedTypes(readMimeTypes(child));
                } else if (child.getTagName().equals(METADATA_TAG)) {
                    parser.setMetadataExtractionPatterns(readMetadataPatterns(child));
                } else if (child.getTagName().equals(PARSER_NAME_TAG)) {
                    parser.setParserName(getString(child));
                } else if (child.getTagName().equals(WIN_TOOL_PATH)) {
                    parser.setToolPath(getString(child));
                } else if (child.getTagName().equals(OUTPUT_CHARSET)) {
                    parser.setCharset(getString(child));
                } else if (child.getTagName().equals(OUTPUT_IS_HTML)) {
                    parser.setOutputHtml(Boolean.valueOf(getString(child)));
                } else if (child.getTagName().equals(LINES_TO_IGNORE)) {
                    parser.setLinesToIgnore(Integer.valueOf(getString(child)));
                }
            }
        }
        if (checkElement != null) {
            String tool = parser.getCommand()[0].split(" ")[0];
            synchronized (lock) {
                boolean firstCheck = (cmdCheckResultCache.get(tool) == null);
                boolean present = readCheckTagAndCheck(checkElement, parser.getToolPath());
                if (!present) {
                    if (firstCheck)
                        LOGGER.error("Error testing " + parser.getParserName()
                                + ". Disable it in conf/ExternalParsers.xml or install '" + tool + "'");
                    return null;
                }
            }
        }

        return parser;
    }

    private static Set<MediaType> readMimeTypes(Element mimeTypes) {
        Set<MediaType> types = new HashSet<MediaType>();

        NodeList children = mimeTypes.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                if (child.getTagName().equals(MIMETYPE_TAG)) {
                    types.add(MediaType.parse(getString(child)));
                }
            }
        }

        return types;
    }

    private static Map<Pattern, String> readMetadataPatterns(Element metadataDef) {
        Map<Pattern, String> metadata = new HashMap<Pattern, String>();

        NodeList children = metadataDef.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                if (child.getTagName().equals(METADATA_MATCH_TAG)) {
                    String metadataKey = child.getAttribute(METADATA_KEY_ATTR);
                    Pattern pattern = Pattern.compile(getString(child));
                    metadata.put(pattern, metadataKey);
                }
            }
        }

        return metadata;
    }

    private static boolean readCheckTagAndCheck(Element checkDef, String toolPath) {
        String command = null;
        List<Integer> errorVals = new ArrayList<Integer>();

        NodeList children = checkDef.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                if (child.getTagName().equals(COMMAND_TAG)) {
                    command = getString(child);
                }
                if (child.getTagName().equals(ERROR_CODES_TAG)) {
                    String errs = getString(child);
                    StringTokenizer st = new StringTokenizer(errs, ",");
                    while (st.hasMoreElements()) {
                        try {
                            String s = st.nextToken();
                            errorVals.add(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                        }
                    }
                }
            }
        }

        if (command != null) {
            String[] theCommand = command.split(" ");
            String tool = theCommand[0];
            if (SystemUtils.IS_OS_WINDOWS && toolPath != null) {
                theCommand[0] = ExternalParser.getRootFolder() + "/" + toolPath + theCommand[0];
            }
            int[] errVals = new int[errorVals.size()];
            for (int i = 0; i < errVals.length; i++) {
                errVals[i] = errorVals.get(i);
            }

            Boolean result = cmdCheckResultCache.get(tool);
            if (result == null) {
                result = ExternalParser.check(theCommand, errVals);
                cmdCheckResultCache.put(tool, result);
            }
            return result;
        }

        // No check command, so assume it's there
        return true;
    }

    private static String getString(Element element) {
        StringBuffer s = new StringBuffer();

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.TEXT_NODE) {
                s.append(node.getNodeValue());
            }
        }

        return s.toString();
    }
}
