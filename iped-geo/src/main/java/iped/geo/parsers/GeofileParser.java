package iped.geo.parsers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.xml.bind.DatatypeConverter;

import org.apache.commons.lang.StringEscapeUtils;
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
import org.geotools.geojson.feature.FeatureJSON;
import org.opengis.feature.simple.SimpleFeature;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

import iped.geo.parsers.kmlstore.FeatureListFactoryRegister;
import iped.geo.parsers.kmlstore.Folder;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;

public class GeofileParser extends AbstractParser {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    public static final MediaType GPX_MIME = MediaType.application("gpx");
    public static final MediaType KML_MIME = MediaType.application("vnd.google-earth.kml+xml");
    public static final MediaType JSON_GOOGLE_MIME = MediaType.application("json-location");

    private static final Set<MediaType> SUPPORTED_TYPES = MediaType.set(GPX_MIME, KML_MIME);
    public static final String ISTRACK = "geo:isTrack";
    public static final String FEATURE_STRING = "geo:featureString";

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tis = TikaInputStream.get(stream, tmp);
        File file = tis.getFile();

        String resourceName = metadata.get("resourceName");
        // String ext = resourceName.substring(resourceName.lastIndexOf("."));
        String mimeType = metadata.get("Indexer-Content-Type");

        try {
            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));
            List<Object> featureList = FeatureListFactoryRegister.getFeatureList(mimeType).parseFeatureList(file);

            int cont = 1;
            int virtualId = 0;
            for (Iterator<Object> iterator = featureList.iterator(); iterator.hasNext();) {
                Object o = iterator.next();
                if (o instanceof SimpleFeature) {
                    SimpleFeature feature = (SimpleFeature) o;
                    String name = feature.getName().getLocalPart();
                    if (name == null)
                        name = "marcador";
                    featureParser(feature, -1, name + cont, handler, metadata, extractor);
                    cont++;
                }
                if (o instanceof Folder) {
                    Folder folder = (Folder) o;

                    virtualId = folderParser(folder, -1, handler, metadata, extractor, virtualId);
                    virtualId = recursiveFolderParse(virtualId, folder, handler, metadata, extractor, context, virtualId);
                }
            }
        } catch (Throwable e) {
            throw new IOException(e);
        }
    }

    public int recursiveFolderParse(int parentId, Folder folder, ContentHandler handler, Metadata metadata, EmbeddedDocumentExtractor extractor, ParseContext context, int virtualId) throws TikaException {
        int cont = 1;

        List<Object> featureList = folder.getFeatures();
        int zerosCount = Integer.toString(featureList.size()).length();
        for (Iterator<Object> iterator = featureList.iterator(); iterator.hasNext();) {
            Object o = iterator.next();
            if (o instanceof SimpleFeature) {
                SimpleFeature feature = (SimpleFeature) o;
                // indica o elemento pai na arvore de exibição
                String name = feature.getName().getLocalPart();
                if (name == null)
                    name = "Marcador";
                String leftZeros = "0".repeat(zerosCount - Integer.toString(cont).length());
                featureParser(feature, parentId, name + leftZeros + cont, handler, metadata, extractor);
                cont++;
            }

            if (o instanceof Folder) {
                Folder subfolder = (Folder) o;
                virtualId = folderParser(subfolder, parentId, handler, metadata, extractor, virtualId);
                virtualId = recursiveFolderParse(virtualId, subfolder, handler, metadata, extractor, context, virtualId);
            }
        }
        return virtualId;
    }

    private int folderParser(Folder folder, int parentId, ContentHandler handler, Metadata metadata, EmbeddedDocumentExtractor extractor, int virtualId) throws TikaException {
        if (extractor.shouldParseEmbedded(metadata)) {
            try {
                ByteArrayInputStream featureStream = new ByteArrayInputStream(folder.getName().getBytes());

                Metadata kmeta = new Metadata();
                kmeta.set(BasicProps.HASCHILD, "true");
                kmeta.set(TikaCoreProperties.CREATED, metadata.get(TikaCoreProperties.CREATED));
                kmeta.set(TikaCoreProperties.MODIFIED, metadata.get(TikaCoreProperties.MODIFIED));
                kmeta.set(HttpHeaders.CONTENT_TYPE, "text/plain");
                kmeta.set(TikaCoreProperties.TITLE, folder.getName());
                int id = ++virtualId;
                kmeta.set(ExtraProperties.ITEM_VIRTUAL_ID, Integer.toString(id));
                kmeta.set(ExtraProperties.PARENT_VIRTUAL_ID, Integer.toString(parentId));
                if (folder.isTrack()) {
                    kmeta.set(ISTRACK, "1");
                    List<Object> features = folder.getFeatures();
                    if (features != null) {
                        StringBuffer jsonArray = new StringBuffer();
                        FeatureJSON fjson = new FeatureJSON();
                        jsonArray.append("[");
                        for (Iterator iterator = features.iterator(); iterator.hasNext();) {
                            Object o = (Object) iterator.next();
                            if (o instanceof SimpleFeature) {
                                SimpleFeature feature = (SimpleFeature) o;
                                StringWriter writer = new StringWriter();
                                fjson.writeFeature(feature, writer);
                                jsonArray.append(writer.toString());
                                jsonArray.append(",");
                            }
                        }
                        jsonArray.append("]");
                        kmeta.set(FEATURE_STRING, jsonArray.toString());
                    }
                }

                extractor.parseEmbedded(featureStream, handler, kmeta, false);

                return id;

            } catch (Exception e) {
                throw new TikaException(e.getMessage(), e);
            }
        }
        return -1;
    }

    private Date toDate(String timestamp) {
        return DatatypeConverter.parseDateTime(timestamp).getTime();
    }

    private void featureParser(SimpleFeature feature, int parentId, String name, ContentHandler handler, Metadata metadata, EmbeddedDocumentExtractor extractor) throws TikaException {
        if (extractor.shouldParseEmbedded(metadata)) {
            try {
                ByteArrayInputStream featureStream = new ByteArrayInputStream(generateFeatureHtml(feature));

                Metadata kmeta = new Metadata();
                kmeta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
                kmeta.set(TikaCoreProperties.CREATED, metadata.get(TikaCoreProperties.CREATED));
                String timestamp = (String) feature.getAttribute("timestamp");
                if (timestamp != null) {
                    kmeta.set(TikaCoreProperties.MODIFIED, toDate(timestamp));
                }
                kmeta.set(HttpHeaders.CONTENT_TYPE, "text/plain");
                kmeta.set(ExtraProperties.PARENT_VIRTUAL_ID, Integer.toString(parentId));

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
                    FeatureJSON fjson = new FeatureJSON();
                    StringWriter writer = new StringWriter();

                    feature.setAttribute("description", StringEscapeUtils.escapeJavaScript(feature.getAttribute("description").toString()));
                    fjson.writeFeature(feature, writer);

                    String str = writer.toString();
                    kmeta.set(FEATURE_STRING, str);
                }
                Coordinate[] coords = p.getCoordinates();
                lon = coords[0].x;
                lat = coords[0].y;
                alt = coords[0].z;

                if (lat != null && lat != 0.0 && lon != null && lon != 0.0) {
                    kmeta.set(ExtraProperties.LOCATIONS, lat + ";" + lon);
                }

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