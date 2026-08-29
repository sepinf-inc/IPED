package iped.geo.parsers.kmlstore;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;

public class KMLParser {
    private static org.slf4j.Logger LOGGER = LoggerFactory.getLogger(KMLParser.class);

    public static List<Object> parse(File file) throws SchemaException, IOException, JDOMException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return parse(fis);
        } catch (Exception e) {
            if (e.getMessage().contains("The prefix \"gx\" for element \"gx:altitudeMode\" is not bound")) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] bytes = fis.readAllBytes();
                    String s = new String(bytes);
                    s = s.replace("<kml ", "<kml xmlns:gx=\"http://www.google.com/kml/ext/2.2\" ");
                    return KMLParser.parse(new ByteArrayInputStream(s.getBytes()));
                } catch (Exception e2) {
                    throw e2;
                }
            }
            if (e.getMessage().contains("byte UTF-8 sequence")) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] bytes = fis.readAllBytes();
                    String s = new String(bytes);
                    s = s.replace("encoding=\"UTF-8\"", "encoding=\"windows-1252\"");
                    return KMLParser.parse(new ByteArrayInputStream(s.getBytes()));
                } catch (Exception e2) {
                    throw e2;
                }
            }
            throw e;
        }
    }

    public static List<Object> parse(InputStream is) throws SchemaException, IOException, JDOMException {
        /*
         * A list to collect features as we create them.
         */
        List<Object> features = new ArrayList<Object>();

        /*
         * GeometryFactory will be used to create the geometry attribute of each
         * feature, using a Point object for the location.
         */
        // GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();

        SimpleFeatureTypeBuilder tb = new SimpleFeatureTypeBuilder();
        tb.setName("placemark");
        tb.add("Geometry", Geometry.class);
        tb.add("name", String.class);
        tb.add("description", String.class);
        tb.add("timestamp", String.class);
        tb.setDefaultGeometry("Geometry");
        SimpleFeatureType placemarkFeatureType = tb.buildFeatureType();
        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(placemarkFeatureType);

        SAXBuilder saxBuilder = new SAXBuilder();
        Document document = saxBuilder.build(is);

        Element kml = document.getRootElement();
        Element placemarks = kml.getChildren().get(0);

        List<Element> pms = placemarks.getChildren();
        
        ArrayList<Exception> exList = new ArrayList<Exception>();

        for (Iterator<Element> iterator = pms.iterator(); iterator.hasNext();) {
            Element ele = iterator.next();

            if (ele.getName().toLowerCase().equals("placemark")) {
                if (isTrack(ele)) {
                    parsePlacemarkTrack(ele, features, featureBuilder);
                } else {
                    features.add(parsePlacemark(ele, featureBuilder, exList));
                }
            }
            if (ele.getName().toLowerCase().equals("folder") || ele.getName().toLowerCase().equals("document")) {
                Folder f = parseFolder(ele, featureBuilder, exList);
                features.add(f);
            }
        }

        if (exList.size() > 0) {
            LOGGER.warn("GeofileParser encountered " + exList.size() + " invalid geo features.");
        }

        return features;
    }

    private static void parsePlacemarkTrack(Element ele, List<Object> features, SimpleFeatureBuilder featureBuilder) {
        List<Element> eles = ele.getChildren();
        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();

        for (Iterator<Element> iterator = eles.iterator(); iterator.hasNext();) {
            Element element = iterator.next();
            if (element.getName().toLowerCase().equals("track")) {
                ArrayList<String> timestamps = new ArrayList<String>();
                List<Element> tracks = element.getChildren();

                for (Iterator<Element> iterator2 = tracks.iterator(); iterator2.hasNext();) {
                    Element element2 = iterator2.next();
                    if (element2.getName().toLowerCase().equals("when")) {
                        timestamps.add(element2.getText());
                    }
                }

                Coordinate[] coords = new Coordinate[timestamps.size()];

                int i = 0;
                for (Iterator<Element> iterator2 = tracks.iterator(); iterator2.hasNext();) {
                    Element element2 = iterator2.next();
                    if (element2.getName().toLowerCase().equals("coord")) {
                        coords[i] = parseCoordinate(element2.getText(), " ,");
                        i++;
                    }
                }

                for (int j = 0; j < coords.length; j++) {
                    String timestamp = timestamps.get(i);
                    Coordinate coord = coords[i];

                    featureBuilder.add(geometryFactory.createPoint(coord));
                    featureBuilder.add(timestamp);
                    featureBuilder.add("Trilha");
                    featureBuilder.add(timestamp);
                    features.add(featureBuilder.buildFeature(null));
                }
            }
        }
    }

    public static boolean isTrack(Element ele) {
        List<Element> eles = ele.getChildren();
        for (Iterator<Element> iterator = eles.iterator(); iterator.hasNext();) {
            Element element = iterator.next();
            String name = element.getName().toLowerCase();
            String namespace = element.getNamespace().getPrefix().toLowerCase();
            if (name.equals("track") && namespace.equals("gx")) {
                return true;
            }
        }
        return false;
    }

    public static SimpleFeature parsePlacemark(Element pm, SimpleFeatureBuilder featureBuilder,
            ArrayList<Exception> exList) {
        List<Element> eles = pm.getChildren();
        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
        Geometry geo = null;
        String name = "";
        String description = "";
        String timestamp = null;

        for (Iterator<Element> iterator = eles.iterator(); iterator.hasNext();) {
            Element ele = iterator.next();

            if (ele.getName().toLowerCase().equals("name")) {
                name = ele.getText();
                continue;
            }
            if (ele.getName().toLowerCase().equals("description")) {
                description = ele.getText();
                continue;
            }
            if (ele.getName().toLowerCase().equals("timestamp")) {
                timestamp = ele.getChildren().get(0).getText();
                continue;
            }
            Geometry geoInt = parseGeometry(ele, geometryFactory, exList);
            if (geoInt != null) {
                geo = geoInt;
            }
        }

        featureBuilder.add(geo);
        featureBuilder.add(name);
        featureBuilder.add(description);
        featureBuilder.add(timestamp);

        return featureBuilder.buildFeature(null);
    }

    static public Coordinate parsePoint(Element ele) {
        List<Element> eles = ele.getChildren();
        Element coordsEle = null;
        for (Iterator<Element> iterator = eles.iterator(); iterator.hasNext();) {
            Element element = iterator.next();
            if (element.getName().toLowerCase().equals("coordinates")) {
                coordsEle = element;
                break;
            }
        }
        return parseCoordinate(coordsEle);
    }

    static public Coordinate parseCoordinate(Element coordsEle) {
        String coordinates = coordsEle.getText();
        return parseCoordinate(coordinates);
    }

    static public Coordinate parseCoordinate(String coordinates) {
        return parseCoordinate(coordinates, ",");
    }

    static public Coordinate parseCoordinate(String coordinates, String tokens) {
        StringTokenizer st = new StringTokenizer(coordinates, tokens);
        double longitude = Double.parseDouble(st.nextToken());
        double latitude = Double.parseDouble(st.nextToken());
        if (st.hasMoreTokens()) {
            double elevation = Double.parseDouble(st.nextToken());
            return new Coordinate(longitude, latitude, elevation);
        } else {
            return new Coordinate(longitude, latitude);
        }
    }

    public static Coordinate[] closePlygonCoords(Coordinate[] coords) {
        Coordinate[] closedCoords = new Coordinate[coords.length + 1];
        for (int j = 0; j < coords.length; j++) {
            closedCoords[j] = coords[j];
        }
        closedCoords[coords.length] = coords[0];
        return closedCoords;
    }

    public static Geometry parseGeometry(Element ele, GeometryFactory geometryFactory, ArrayList<Exception> exList) {
        Geometry geo = null;

        try {
            if (ele.getName().toLowerCase().equals("point")) {
                Coordinate coord = parsePoint(ele);
                geo = geometryFactory.createPoint(coord);
            }

            if (ele.getName().toLowerCase().equals("linestring") || ele.getName().toLowerCase().equals("linearring")) {
                List<Element> eles = ele.getChildren();
                Element coordsEle = null;
                for (Iterator<Element> iterator = eles.iterator(); iterator.hasNext();) {
                    Element element = iterator.next();
                    if (element.getName().toLowerCase().equals("coordinates")) {
                        coordsEle = element;
                        break;
                    }
                }

                String coordinates = coordsEle.getText().trim();
                StringTokenizer st = new StringTokenizer(coordinates, " \t\n\r");
                Coordinate[] coords = new Coordinate[st.countTokens()];
                int i = 0;
                while (st.hasMoreTokens()) {
                    String tok = st.nextToken(" ");
                    coords[i] = parseCoordinate(tok);
                    i++;
                }

                if (coords.length < 4) {
                    geo = geometryFactory.createLineString(coords);
                } else {
                    if (ele.getName().toLowerCase().equals("linestring")) {
                        geo = geometryFactory.createLineString(coords);
                    } else {
                        try {
                            geo = geometryFactory.createLinearRing(coords);
                        } catch (IllegalArgumentException e) {
                            if (e.getMessage().contains("closed linestring")) {
                                try {
                                    geo = geometryFactory.createLineString(coords);
                                } catch (Exception e2) {
                                    throw e2;
                                }
                            }
                        }
                    }
                }
            }

            if (ele.getName().toLowerCase().equals("polygon")) {
                Element boundary = null;
                List<Element> eles = ele.getChildren();
                for (Iterator<Element> iterator = eles.iterator(); iterator.hasNext();) {
                    Element element = iterator.next();
                    if (element.getName().toLowerCase().equals("outerboundaryis")) {
                        boundary = element;
                        break;
                    }
                    if (element.getName().toLowerCase().equals("innerboundaryis")) {
                        boundary = element;
                        break;
                    }
                }

                if (boundary != null) {
                    geo = parseGeometry(boundary.getChildren().get(0), geometryFactory, exList);
                }
            }

            if (ele.getName().toLowerCase().equals("multigeometry")) {
                List<Geometry> geos = new ArrayList<Geometry>();

                List<Element> eles = ele.getChildren();
                for (Iterator<Element> iterator = eles.iterator(); iterator.hasNext();) {
                    Geometry subgeo = parseGeometry(iterator.next(), geometryFactory, exList);
                    if (subgeo != null) {
                        geos.add(subgeo);
                    }
                }
                geo = geometryFactory.buildGeometry(geos);
            }
        } catch (Exception e) {
            exList.add(e);
        }

        return geo;
    }

    public static Folder parseFolder(Element pm, SimpleFeatureBuilder featureBuilder, ArrayList<Exception> exList) {
        List<Object> features = new ArrayList<Object>();
        Folder folder = new Folder();

        List<Element> eles = pm.getChildren();
        for (Iterator<Element> iterator = eles.iterator(); iterator.hasNext();) {
            Element ele = iterator.next();
            try {
                if (ele.getName().toLowerCase().equals("placemark")) {
                    features.add(parsePlacemark(ele, featureBuilder, exList));
                }
                if (ele.getName().toLowerCase().equals("folder") || ele.getName().toLowerCase().equals("document")) {
                    features.add(parseFolder(ele, featureBuilder, exList));
                }
                if (ele.getName().toLowerCase().equals("name")) {
                    folder.setName(ele.getText());
                }
                if (ele.getName().toLowerCase().equals("extendeddata")) {
                    Element data = ele.getChild("Data");
                    if (data != null && data.getAttribute("name") != null && data.getAttribute("name").getValue().equals("iped.geo.track")) {
                        folder.setTrack(true);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        folder.setFeatures(features);
        return folder;
    }
}
