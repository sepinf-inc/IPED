package dpf.mt.gpinf.mapas.parsers.kmlstore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.tika.io.TemporaryResources;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import dpf.mt.gpinf.mapas.parsers.GeofileParser;

public class GPXFeatureListFactory implements FeatureListFactory {

    @Override
    public boolean canParse(String mimeType) {
        return GeofileParser.GPX_MIME.toString().equals(mimeType);
    }

    @Override
    public List<Object> parseFeatureList(File file) throws IOException {
        try {
            TemporaryResources tmp = new TemporaryResources();
            File srcFile = tmp.createTemporaryFile();
            xslTransform(file, srcFile, GeofileParser.class.getResourceAsStream("gpxtokml.xsl"));

            return KMLParser.parse(srcFile);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public static void xslTransform(File srcFile, File destFile, InputStream xslStream)
            throws ParserConfigurationException, FileNotFoundException, SAXException, IOException,
            TransformerException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new FileInputStream(srcFile));
        TransformerFactory tFactory = TransformerFactory.newInstance();
        StreamSource stylesource = new StreamSource(xslStream);
        Transformer transformer = tFactory.newTransformer(stylesource);
        DOMSource source = new DOMSource(document);
        StreamResult result = new StreamResult(destFile);
        transformer.transform(source, result);
    }

}
