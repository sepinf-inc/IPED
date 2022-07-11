package iped.viewers.timelinegraph.datasets;

import java.io.IOException;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedSetDocValues;

import iped.app.ui.App;
import iped.engine.task.index.IndexItem;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;

public class LuceneDataset {
	SortedSetDocValues docValuesSet;
	SortedSetDocValues eventDocValuesSet;
	LeafReader reader;

    private void loadDocValues(String field) throws IOException {
        reader = App.get().appCase.getLeafReader();
    	
        // System.out.println("getDocValues");
        docValuesSet = reader.getSortedSetDocValues(field);
        if (BasicProps.TIME_EVENT.equals(field)) {
            eventDocValuesSet = reader.getSortedSetDocValues(ExtraProperties.TIME_EVENT_GROUPS);
        }
    }

}
