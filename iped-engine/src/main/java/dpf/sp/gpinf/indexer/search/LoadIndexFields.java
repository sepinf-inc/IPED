package dpf.sp.gpinf.indexer.search;

import java.util.HashSet;
import java.util.List;

import org.apache.lucene.index.IndexReader;

import dpf.sp.gpinf.indexer.process.IndexItem;
import iped3.IIPEDSource;

public class LoadIndexFields {

    public static String[] getFields(List<? extends IIPEDSource> sources) {

        HashSet<String> names = new HashSet<String>();
        for (IIPEDSource source : sources) {
            IndexReader leafReader = source.getReader();
            leafReader.leaves().forEach(ctx -> ctx.reader().getFieldInfos().forEach(info -> {
                if (!IndexItem.CONTENT.equals(info.name))
                    names.add(info.name);
            }));
        }

        return names.toArray(new String[0]);
    }

}
