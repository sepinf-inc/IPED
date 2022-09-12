package iped.parsers.util;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.tika.exception.TikaException;
import org.apache.tika.mime.MediaType;
import org.apache.tika.utils.XMLReaderUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import iped.parsers.external.ExternalParsersConfigReaderMetKeys;

public class ExternalParserConfigGenerator implements ExternalParsersConfigReaderMetKeys {
    private Document document;
    private Element root, winToolPath, parser, name, check, checkCommand,
        errorCodes, command, mimeTypes, metadata, outputCharset, firstLinesToIgnore;

    public ExternalParserConfigGenerator() throws TikaException, TransformerException {
        DocumentBuilder docBuilder = XMLReaderUtils.getDocumentBuilder();
        document = docBuilder.newDocument();

        root = document.createElement(EXTERNAL_PARSERS_TAG);
        document.appendChild(root);
        parser = document.createElement(PARSER_TAG);
        root.appendChild(parser);
        name = document.createElement(PARSER_NAME_TAG);
        parser.appendChild(name);
        winToolPath = document.createElement(WIN_TOOL_PATH);
        parser.appendChild(winToolPath);
        check = document.createElement(CHECK_TAG);
        parser.appendChild(check);
        checkCommand = document.createElement(COMMAND_TAG);
        check.appendChild(checkCommand);
        errorCodes = document.createElement(ERROR_CODES_TAG);
        check.appendChild(errorCodes);
        command = document.createElement(COMMAND_TAG);
        parser.appendChild(command);
        mimeTypes = document.createElement(MIMETYPES_TAG);
        parser.appendChild(mimeTypes);
        metadata = document.createElement(METADATA_TAG);
        parser.appendChild(metadata);
        outputCharset = document.createElement(OUTPUT_CHARSET);
        parser.appendChild(outputCharset);
        firstLinesToIgnore = document.createElement(LINES_TO_IGNORE);
        parser.appendChild(firstLinesToIgnore);

    }

    private void addTextContent(Element element, String text) {
        element.appendChild(document.createTextNode(text));
    }

    public void setParserName(String strName) {
        addTextContent(name, strName);
    }
    
    public void setCheckCommand(String strCheckCommand) {
        addTextContent(checkCommand, strCheckCommand);
    }
    
    public void setErrorCodes(int... intErrorCodes) {
        String strErrorCodes = Arrays.toString(intErrorCodes).replaceAll("\\[|\\]|\\s", "");
        addTextContent(errorCodes, strErrorCodes);
    }

    public void setCommand(String strCommand) {
        addTextContent(command, strCommand);
    }

    public void setOutputCharset(String strOutputCharset) {
        addTextContent(outputCharset, strOutputCharset);
    }

    public void setWinToolPath(String strWinToolPath) {
        addTextContent(winToolPath, strWinToolPath);
    }

    public void setFirstLinesToIgnore(int intFirstLinesToIgnore) {
        String strFirstLinesToIgnore = String.valueOf(intFirstLinesToIgnore);
        addTextContent(firstLinesToIgnore, strFirstLinesToIgnore);
    }

    public void addMimeTypes(HashSet<MediaType> mediaTypes) {
        for (MediaType mediaType : mediaTypes) {
            Element mimeType = document.createElement(MIMETYPE_TAG);
            mimeTypes.appendChild(mimeType);
            addTextContent(mimeType, mediaType.toString());
        }
    }

    public void addMatchPatterns(String... matchPatterns) {
        for (int i = 0; i < matchPatterns.length; i++) {
            Element match = document.createElement(METADATA_MATCH_TAG);
            metadata.appendChild(match);
            match.setAttribute("key", "pattern_"+i);
            addTextContent(match, matchPatterns[i]);
        }
    }

    public void writeDocumentToFile(File file) throws TransformerException, ParserConfigurationException, SAXException, IOException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        if (file.exists()) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document originalDoc = documentBuilder.parse(file);

            Node originalExternalParsersTag = originalDoc.getFirstChild();
            Node parserTag = originalDoc.importNode(document.getFirstChild().getFirstChild(), true);
            originalExternalParsersTag.appendChild(parserTag);

            document = originalDoc;
            // transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        } else {
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            // transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "1");
        }
        DOMSource source = new DOMSource(document);
        StreamResult result = new StreamResult(file);
        transformer.transform(source, result);
    }

}