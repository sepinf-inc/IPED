package iped.viewers.api;

import java.util.List;

/*
 * Create and provide a list of resultSetViewers according to some configuration implementation.
 */

public interface ResultSetViewerConfiguration {

    List<ResultSetViewer> getResultSetViewers();

}
