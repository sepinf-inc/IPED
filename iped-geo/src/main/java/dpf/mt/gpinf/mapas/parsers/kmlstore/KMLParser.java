package dpf.mt.gpinf.mapas.parsers.kmlstore;

import java.io.File;
import java.io.IOException;
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

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;

public class KMLParser {
	
	public static List<Object> parse(File file) throws SchemaException, IOException, JDOMException{
		/*
         * A list to collect features as we create them.
         */
        List<Object> features = new ArrayList<Object>();

        /*
         * GeometryFactory will be used to create the geometry attribute of each feature,
         * using a Point object for the location.
         */
        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
        
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
        Document document = saxBuilder.build(file);

        Element kml = document.getRootElement();
        Element placemarks = kml.getChildren().get(0);
        List<Element> pms = placemarks.getChildren();

        for (Iterator<Element> iterator = pms.iterator(); iterator.hasNext();) {
        	Element ele = iterator.next();
        	
        	if(ele.getName().toLowerCase().equals("placemark")){
        		if(isTrack(ele)){
        			parsePlacemarkTrack(ele, features, featureBuilder);
        		}else{
            		features.add(parsePlacemark(ele, featureBuilder));
        		}
        	}
        	if(ele.getName().toLowerCase().equals("folder")){
        		features.add(parseFolder(ele, featureBuilder));
        	}
        }

        return features;
	}

	private static void parsePlacemarkTrack(Element ele, List<Object> features, SimpleFeatureBuilder featureBuilder) {
        List<Element> eles = ele.getChildren();
        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
        for (Iterator<Element> iterator = eles.iterator(); iterator.hasNext();) {
        	Element element = iterator.next();
        	if(element.getName().toLowerCase().equals("track")){
        		List<Element> tracks = element.getChildren();
        		String timestamp=null, name=null;
        		Coordinate coord=null;

        		for (Iterator iterator2 = tracks.iterator(); iterator2.hasNext();) {
    				Element element2 = (Element) iterator2.next();
    				if(element2.getName().toLowerCase().equals("when")){
    					timestamp = element2.getText();
    					name = element2.getText();
    				}
    				if(element2.getName().toLowerCase().equals("coord")){
    					coord = parseCoordinate(element2.getText(), " ");
    				}
    				if((timestamp!=null)&&(coord!=null)){
    	        		featureBuilder.add(geometryFactory.createPoint(coord));
    	                featureBuilder.add(name);
    	                featureBuilder.add("Trilha");
    	               	featureBuilder.add(timestamp);
    	               	features.add(featureBuilder.buildFeature(null));
    	               	timestamp=null;
    	               	coord=null;
    				}
    			}
        		
               	
        	}
        }
	}

	public static boolean isTrack(Element ele){
        List<Element> eles = ele.getChildren();
        for (Iterator<Element> iterator = eles.iterator(); iterator.hasNext();) {
        	Element element = iterator.next();
        	String name = element.getName().toLowerCase();
        	String namespace = element.getNamespace().getPrefix().toLowerCase();
        	if(name.equals("track")&&namespace.equals("gx")){
        		return true;
        	}        	
        }
		return false;
	}

	public static SimpleFeature parsePlacemark(Element pm, SimpleFeatureBuilder featureBuilder){
        List<Element> eles = pm.getChildren();
        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
    	Geometry geo = null;
    	String name = "";
    	String description = "";
    	String timestamp = null;

        for (Iterator<Element> iterator = eles.iterator(); iterator.hasNext();) {
        	Element ele = iterator.next();

        	Geometry geoInt = parseGeometry(ele, geometryFactory);
        	if(geoInt!=null){
        		geo = geoInt;
        	}
        	if(ele.getName().toLowerCase().equals("name")){
        		name = ele.getText();
        	}
        	if(ele.getName().toLowerCase().equals("description")){
        		description = ele.getText();
        	}
        	if(ele.getName().toLowerCase().equals("timestamp")){
        		timestamp = ele.getChildren().get(0).getText();
        	}
        }

		featureBuilder.add(geo);
        featureBuilder.add(name);
        featureBuilder.add(description);
       	featureBuilder.add(timestamp);

        return featureBuilder.buildFeature(null);
	}
	
	static public Coordinate parsePoint(Element ele){
		List<Element> eles = ele.getChildren();
		Element coordsEle = null;
		for (Iterator<Element> iterator = eles.iterator(); iterator.hasNext();) {
			Element element = iterator.next();
			if(element.getName().toLowerCase().equals("coordinates")){
				coordsEle = element;
				break;
			}
		}
		return parseCoordinate(coordsEle);
	}

	static public Coordinate parseCoordinate(Element coordsEle){
		String coordinates = coordsEle.getText();
		return parseCoordinate(coordinates);
	}

	static public Coordinate parseCoordinate(String coordinates){
		return parseCoordinate(coordinates, ",");
	}
	
	static public Coordinate parseCoordinate(String coordinates, String tokens){
		StringTokenizer st = new StringTokenizer(coordinates, tokens);
		double longitude = Double.parseDouble(st.nextToken());
		double latitude = Double.parseDouble(st.nextToken());
		if(st.hasMoreTokens()){
			double elevation = Double.parseDouble(st.nextToken());
			return new Coordinate(longitude, latitude, elevation);
		}else{
			return new Coordinate(longitude, latitude);
		}
	}
		
	public static Geometry parseGeometry(Element ele, GeometryFactory geometryFactory){
		Geometry geo = null;

		if(ele.getName().toLowerCase().equals("point")){
			Coordinate coord = parsePoint(ele);
    		geo = geometryFactory.createPoint(coord);
    	}

    	if(ele.getName().toLowerCase().equals("linestring")){
    		List<Element> eles = ele.getChildren();
    		Element coordsEle = null;
    		for (Iterator<Element> iterator = eles.iterator(); iterator.hasNext();) {
    			Element element = iterator.next();
    			if(element.getName().toLowerCase().equals("coordinates")){
    				coordsEle = element;
    				break;
    			}
    		}
    		
    		String coordinates = coordsEle.getText();
    		StringTokenizer st = new StringTokenizer(coordinates, " ");
    		Coordinate[] coords = new Coordinate[st.countTokens()];
    		int i=0;
    		while(st.hasMoreTokens()){
    			String tok = st.nextToken(" ");
        		coords[i] = parseCoordinate(tok);
        		i++;
    		}
    		geo = geometryFactory.createLineString(coords);
    		
    	}

    	if(ele.getName().toLowerCase().equals("polygon")){
    		Element boundary = null;
    		List<Element> eles = ele.getChildren();
    		for (Iterator iterator = eles.iterator(); iterator.hasNext();) {
    			Element element = (Element) iterator.next();
    			if(element.getName().toLowerCase().equals("outerboundaryis")){
    				boundary = element;
    				break;
    			}
    			if(element.getName().toLowerCase().equals("innerboundaryis")){
    				boundary = element;
    				break;
    			}
			}

    		if( boundary != null ){
        		String coordinates = boundary.getChildren().get(0).getChildren().get(0).getText();

        		StringTokenizer st = new StringTokenizer(coordinates, " ");
        		Coordinate[] coords = new Coordinate[st.countTokens()];
        		int i=0;
        		while(st.hasMoreTokens()){
        			String tok = st.nextToken(" ");
            		coords[i] = parseCoordinate(tok);
            		i++;
        		}
        		geo = geometryFactory.createPolygon(coords);
    		}
    	}

    	if(ele.getName().toLowerCase().equals("multigeometry")){
    		List<Geometry> geos = new ArrayList<Geometry>();

            List<Element> eles = ele.getChildren();
            for (Iterator<Element> iterator = eles.iterator(); iterator.hasNext();) {
            	Geometry subgeo = parseGeometry(iterator.next(), geometryFactory);
            	if(subgeo!=null){
            		geos.add(subgeo);
            	}
            }
            geo = geometryFactory.buildGeometry(geos);
    	}

		return geo;		
	}

	public static Folder parseFolder(Element pm, SimpleFeatureBuilder featureBuilder){
        List<Object> features = new ArrayList<Object>();
        Folder folder = new Folder();

		List<Element> eles = pm.getChildren();
        for (Iterator<Element> iterator = eles.iterator(); iterator.hasNext();) {
        	Element ele = iterator.next();
        	if(ele.getName().toLowerCase().equals("placemark")){
        		features.add(parsePlacemark(ele, featureBuilder));
        	}
        	if(ele.getName().toLowerCase().equals("folder")){
        		features.add(parseFolder(ele, featureBuilder));
        	}
        	if(ele.getName().toLowerCase().equals("name")){
        		folder.setName(ele.getText());
        	}
        }
        
        folder.setFeatures(features);
        return folder;		
	}
}
