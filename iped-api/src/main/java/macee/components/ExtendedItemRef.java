package macee.components;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import macee.CaseItem;

public class ExtendedItemRef extends SimpleItemRef implements ItemRef {

    private Map properties = null;
    private String name = "";

    public ExtendedItemRef(int id, String sourceId, String caseId) {
        super(id, sourceId, caseId);
    }

    public ExtendedItemRef(int id, String name, String sourceId, String caseId) {
        this(id, sourceId, caseId);
        this.name = name;
    }

    public ExtendedItemRef(int id, String name, String sourceId, String caseId, Properties props) {
        this(id, sourceId, caseId);
        this.name = name;
        this.properties = new HashMap();
        this.properties.putAll(props);
    }

    public static ExtendedItemRef createExtended(CaseItem item) {
        if (item == null) {
            return null;
        }
        return new ExtendedItemRef(item.getId(), item.getName(), item.getDataSourceId(), item.getCaseId());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map getProperties() {
        if (properties == null) {
            this.properties = new HashMap<>();
        }
        return properties;
    }
}
