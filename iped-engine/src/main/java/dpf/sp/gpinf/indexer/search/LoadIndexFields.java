package dpf.sp.gpinf.indexer.search;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.lucene.index.Fields;
import org.apache.lucene.index.LeafReader;

import dpf.sp.gpinf.indexer.process.IndexItem;

public class LoadIndexFields {

    public static String[] addExtraFields(LeafReader atomicReader, String[] defaultFields) {

        ArrayList<String> names = new ArrayList<String>();
        for (String f : defaultFields) {
            names.add(f);
        }
        try {
            Fields fields = atomicReader.fields();
            // Arrays.sort(fields);
            for (String f : fields) {
                if (!IndexItem.CONTENT.equals(f) && !names.contains(f)) {
                    names.add(f);
                }
            }

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return names.toArray(new String[0]);
    }

}
