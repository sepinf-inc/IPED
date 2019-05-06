package dpf.mt.gpinf.mapas.parsers.kmlstore;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FeatureListFactoryRegister {
	//singleton methods
	static List<FeatureListFactory> registeredFactories = new ArrayList<FeatureListFactory>();
	
	static public FeatureListFactory getFeatureList(String mimeType){
		for (Iterator<FeatureListFactory> iterator = registeredFactories.iterator(); iterator.hasNext();) {
			FeatureListFactory featureListFactory = (FeatureListFactory) iterator.next();
			if(featureListFactory.canParse(mimeType)){
				return featureListFactory; 
			}
		}
		return null;
	}

	static public void addFeatureList(FeatureListFactory f){
		registeredFactories.add(f);
	}

	static public void removeFeatureList(FeatureListFactory f){
		registeredFactories.remove(f);
	}

	//registers the features list factories
	static {
		//this code can evolve to load dinamically the registered feature list factories
		FeatureListFactoryRegister.addFeatureList(new KMLFeatureListFactory());
		FeatureListFactoryRegister.addFeatureList(new GPXFeatureListFactory());
	}

}
