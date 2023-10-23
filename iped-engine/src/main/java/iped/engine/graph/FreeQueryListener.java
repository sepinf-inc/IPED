package iped.engine.graph;

import java.util.List;
import java.util.Map;

public interface FreeQueryListener {

    void columnsFound(List<String> columns);

    void resultFound(Map<String, Object> next);

}
