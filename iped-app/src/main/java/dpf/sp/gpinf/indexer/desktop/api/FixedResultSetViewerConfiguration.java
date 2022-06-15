package dpf.sp.gpinf.indexer.desktop.api;

import java.util.ArrayList;
import java.util.List;

import iped.geo.impl.MapaViewer;
import iped.viewers.api.ResultSetViewer;
import iped.viewers.api.ResultSetViewerConfiguration;

public class FixedResultSetViewerConfiguration implements ResultSetViewerConfiguration {

    List<ResultSetViewer> viewers = new ArrayList<ResultSetViewer>();

    @Override
    public List<ResultSetViewer> getResultSetViewers() {
        if (viewers.size() == 0) {
            ResultSetViewer mapa = new MapaViewer();
            viewers.add(mapa);
        }
        return viewers;
    }

}
