package dpf.sp.gpinf.indexer.desktop.api;

import java.util.ArrayList;
import java.util.List;

import iped3.desktop.ResultSetViewer;
import iped3.desktop.ResultSetViewerConfiguration;

public class FixedResultSetViewerConfiguration implements ResultSetViewerConfiguration {

	List<ResultSetViewer> viewers = new ArrayList<ResultSetViewer>();

	@Override
	public List<ResultSetViewer> getResultSetViewers() {
		try {
			if(viewers.size()==0) {
	            Class<?> mapaClass = Class.forName("dpf.mt.gpinf.mapas.impl.MapaViewer");
				ResultSetViewer mapa = (ResultSetViewer) mapaClass.newInstance();
				viewers.add(mapa);
			}
			return viewers;
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
			/*retorna lista vazia*/
			return new ArrayList<ResultSetViewer>();
		} 
	}

}
