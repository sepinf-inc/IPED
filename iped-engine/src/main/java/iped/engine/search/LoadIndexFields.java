package iped.engine.search;

import java.util.HashSet;
import java.util.List;

import org.apache.lucene.index.IndexReader;

import iped.data.IIPEDSource;
import iped.engine.task.index.IndexItem;

public class LoadIndexFields {

    public static String[] getFields(List<? extends IIPEDSource> sources) {

        HashSet<String> names = new HashSet<String>();
        for (IIPEDSource source : sources) {
            IndexReader leafReader = source.getReader();
            leafReader.leaves().forEach(ctx -> ctx.reader().getFieldInfos().forEach(info -> {
                if (!IndexItem.CONTENT.equals(info.name) && !info.name.startsWith(IndexItem.GEO_SSDV_PREFIX)
                        && (!info.name.startsWith(SimilarFacesSearch.FACE_FEATURES)
                                || info.name.equals(SimilarFacesSearch.FACE_FEATURES))) {
                    names.add(info.name);
                }
            }));
        }

        return names.toArray(new String[0]);
    }

}
