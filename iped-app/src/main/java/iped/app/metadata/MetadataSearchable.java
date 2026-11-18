package iped.app.metadata;

import java.io.IOException;
import java.util.HashMap;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.SortedSetDocValues;

import iped.app.ui.App;
import iped.engine.task.index.IndexItem;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;

public class MetadataSearchable {
    protected volatile NumericDocValues numValues;
    protected volatile SortedNumericDocValues numValuesSet;
    protected volatile SortedDocValues docValues;
    protected volatile SortedSetDocValues docValuesSet;
    protected volatile SortedSetDocValues eventDocValuesSet;
    protected volatile HashMap<String, long[]> eventSetToOrdsCache = new HashMap<>();

    protected volatile static LeafReader reader;

    volatile boolean isCategory = false;

    public MetadataSearchable() {
    }

    public MetadataSearchable(String field) throws IOException {
        loadDocValues(field);
    }

    protected void loadDocValues(String field) throws IOException {
        reader = App.get().appCase.getLeafReader();
        // System.out.println("getDocValues");
        numValues = reader.getNumericDocValues(field);
        numValuesSet = reader.getSortedNumericDocValues(field);
        docValues = reader.getSortedDocValues(field);
        String prefix = ExtraProperties.LOCATIONS.equals(field) ? IndexItem.GEO_SSDV_PREFIX : "";
        docValuesSet = reader.getSortedSetDocValues(prefix + field);
        if (BasicProps.TIME_EVENT.equals(field)) {
            eventDocValuesSet = reader.getSortedSetDocValues(ExtraProperties.TIME_EVENT_GROUPS);
        }
        isCategory = BasicProps.CATEGORY.equals(field);
        eventSetToOrdsCache.clear();
    }

    public boolean isSingleValuedField() {
        return numValues != null || docValues != null;
    }

}
