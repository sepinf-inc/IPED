package dpf.mt.gpinf.mapas.parsers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.xml.bind.DatatypeConverter;

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
import org.opengis.feature.simple.SimpleFeature;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

import dpf.mt.gpinf.mapas.parsers.kmlstore.FeatureListFactoryRegister;
import dpf.mt.gpinf.mapas.parsers.kmlstore.Folder;
import dpf.sp.gpinf.indexer.parsers.util.EmbeddedItem;
import dpf.sp.gpinf.indexer.parsers.util.EmbeddedParent;
import iped3.util.BasicProps;

public class GeofileParser extends AbstractParser {

    public static final MediaType GPX_MIME = MediaType.application("gpx");
    public static final MediaType KML_MIME = MediaType.application("vnd.google-earth.kml+xml");
    public static final MediaType JSON_GOOGLE_MIME = MediaType.application("json-location");

    private static final Set<MediaType> SUPPORTED_TYPES = MediaType.set(GPX_MIME, KML_MIME);

    HashMap<String, EmbeddedParent> parentMap = new HashMap<String, EmbeddedParent>();

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tis = TikaInputStream.get(stream, tmp);
        File file = tis.getFile();

        String resourceName = metadata.get("resourceName");
        String ext = resourceName.substring(resourceName.lastIndexOf("."));
        String mimeType = metadata.get("Indexer-Content-Type");

        try {
            context.set(EmbeddedParent.class, null);
            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                    new ParsingEmbeddedDocumentExtractor(context));

            List<Object> featureList = FeatureListFactoryRegister.getFeatureList(mimeType).parseFeatureList(file);

            int cont = 1;
            for (Iterator<Object> iterator = featureList.iterator(); iterator.hasNext();) {
                Object o = iterator.next();
                if (o instanceof SimpleFeature) {
                    SimpleFeature feature = (SimpleFeature) o;
                    String name = feature.getName().getLocalPart();
                    if (name == null)
                        name = "marcador";
                    featureParser(feature, name + cont, handler, metadata, extractor);
                    cont++;
                }
                if (o instanceof Folder) {
                    Folder folder = (Folder) o;
                    context.set(EmbeddedParent.class, null);
                    folderParser(folder, "", handler, metadata, extractor);
                    parentMap.put(folder.getName(), context.get(EmbeddedItem.class));
                    recursiveFolderParse(folder.getName(), folder, handler, metadata, extractor, context);
                }
            }
        } catch (Throwable e) {
            throw new IOException(e);
        }
    }

    public void recursiveFolderParse(String parentPath, Folder folder, ContentHandler handler, Metadata metadata,
            EmbeddedDocumentExtractor extractor, ParseContext context) throws TikaException {
        int cont = 1;

        List<Object> featureList = folder.getFeatures();
        EmbeddedParent parent = parentMap.get(parentPath);
        for (Iterator<Object> iterator = featureList.iterator(); iterator.hasNext();) {
            Object o = iterator.next();
            if (o instanceof SimpleFeature) {
                SimpleFeature feature = (SimpleFeature) o;
                // indica o elemento pai na arvore de exibição
                context.set(EmbeddedParent.class, parent);
                String name = feature.getName().getLocalPart();
                if (name == null)
                    name = "Marcador";
                featureParser(feature, name + cont, handler, metadata, extractor);
                cont++;
            }

            if (o instanceof Folder) {
                Folder subfolder = (Folder) o;
                context.set(EmbeddedParent.class, parentMap.get(parentPath));
                String keyPath = parentPath + "/" + folder.getName();
                folderParser(subfolder, keyPath, handler, metadata, extractor);
                parentMap.put(keyPath, context.get(EmbeddedItem.class));
                recursiveFolderParse(keyPath, subfolder, handler, metadata, extractor, context);
            }
        }
    }

    private void folderParser(Folder folder, String parentPath, ContentHandler handler, Metadata metadata,
            EmbeddedDocumentExtractor extractor) throws TikaException {
        if (extractor.shouldParseEmbedded(metadata)) {
            try {
                ByteArrayInputStream featureStream = new ByteArrayInputStream(folder.getName().getBytes());

                Metadata kmeta = new Metadata();
                kmeta.set(BasicProps.HASCHILD, "true");
                kmeta.set(TikaCoreProperties.CREATED, metadata.get(TikaCoreProperties.CREATED));
                kmeta.set(TikaCoreProperties.MODIFIED, metadata.get(TikaCoreProperties.MODIFIED));
                kmeta.set(HttpHeaders.CONTENT_TYPE, "text/plain");
                kmeta.set(TikaCoreProperties.TITLE, folder.getName());

                extractor.parseEmbedded(featureStream, handler, kmeta, false);

            } catch (Exception e) {
                throw new TikaException(e.getMessage(), e);
            }
        }
    }

    private Date toDate(String timestamp) {
        return DatatypeConverter.parseDateTime(timestamp).getTime();
    }

    private void featureParser(SimpleFeature feature, String name, ContentHandler handler, Metadata metadata,
            EmbeddedDocumentExtractor extractor) throws TikaException {
        if (extractor.shouldParseEmbedded(metadata)) {
            try {
                ByteArrayInputStream featureStream = new ByteArrayInputStream(generateFeatureHtml(feature));

                Metadata kmeta = new Metadata();
                kmeta.set(TikaCoreProperties.CREATED, metadata.get(TikaCoreProperties.CREATED));
                String timestamp = (String) feature.getAttribute("timestamp");
                if (timestamp != null) {
                    kmeta.set(TikaCoreProperties.MODIFIED, toDate(timestamp));
                }
                kmeta.set(HttpHeaders.CONTENT_TYPE, "text/plain");
                String title = (String) feature.getAttribute("name");
                if (title.trim().equals(""))
                    title = name;
                kmeta.set(TikaCoreProperties.TITLE, title);

                Double lon = null;
                Double lat = null;
                Double alt = null;
                Geometry g = (Geometry) feature.getDefaultGeometry();
                Point p = null;
                if (g instanceof Point) {
                    p = (Point) g;
                } else {
                    p = g.getCentroid();
                }
                Coordinate[] coords = p.getCoordinates();
                lon = coords[0].x;
                lat = coords[0].y;
                alt = coords[0].z;

                kmeta.set(Metadata.LATITUDE, lat);
                kmeta.set(Metadata.LONGITUDE, lon);
                if (alt != null) {
                    kmeta.set(Metadata.ALTITUDE, alt);
                }

                extractor.parseEmbedded(featureStream, handler, kmeta, false);
            } catch (Exception e) {
                throw new TikaException(e.getMessage(), e);
            }
        }
    }

    private byte[] generateFeatureHtml(SimpleFeature feat) throws UnsupportedEncodingException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, "UTF-8"));
        Object o = feat.getDefaultGeometryProperty().getValue();
        out.println(o.toString());
        out.println(feat.toString());
        out.flush();
        return bout.toByteArray();
    }
}