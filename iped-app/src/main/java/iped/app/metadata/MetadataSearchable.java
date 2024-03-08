package iped.app.metadata;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

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
    private String field;

    public MetadataSearchable() {
    }

    public MetadataSearchable(String field) throws IOException {
        this.field = field;
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

    Iterator empty = new Iterator<String>() {
        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public String next() {
            return null;
        }
    };

    public Iterator<String> getIterator(int doc) {
        try {
            loadDocValues(field);
            boolean adv = docValuesSet.advanceExact(doc);

            if (adv) {
                return empty;
            }

            return new Iterator<String>() {
                long ord = -1;

                @Override
                public boolean hasNext() {
                    try {
                        ord = docValuesSet.nextOrd();
                        return ord != SortedSetDocValues.NO_MORE_ORDS;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return false;
                }

                @Override
                public String next() {
                    try {
                        return docValuesSet.lookupOrd(ord).utf8ToString();
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                }
            };
        } catch (IOException e) {
            e.printStackTrace();
        }
        return empty;
    }

}
