package iped.app.ui.filters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;

import iped.app.ui.Messages;
import iped.data.IItemId;
import iped.engine.data.IPEDMultiSource;
import iped.engine.data.ItemId;
import iped.engine.search.MultiSearchResult;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.properties.BasicProps;
import iped.search.IMultiSearchResult;
import iped.utils.DateUtil;
import iped.viewers.api.IMutableFilter;
import iped.viewers.api.IResultSetFilter;

/*
 * Filters items with content timestamp equivalent to file filesystem metadata timestamp
 * 
 * @author: Patrick Dalla Bernardina
 */
public class ProducedFilter implements IResultSetFilter, IMutableFilter {

    private static final List<String> internalCreatedFieldName = new ArrayList<String>();
    public static final String FILTER_NAME = "[" + Messages.getString("ProducedFilter.Produced") + "]";

    // 1 minute is the acceptable date difference between FS date and content date
    private static final int PRODUCTION_TIMERANGE = 60 * 1000;

    static {
        internalCreatedFieldName.add("common:dcterms:created");
        internalCreatedFieldName.add("image:Exif SubIFD:Date/Time Original");
        internalCreatedFieldName.add("image:Exif IFD0:Date/Time");
    }

    @Override
    public IMultiSearchResult filterResult(IMultiSearchResult src)
            throws ParseException, QueryNodeException, IOException {
        IPEDMultiSource ipedCase = (IPEDMultiSource) src.getIPEDSource();
        LeafReader reader = ipedCase.getLeafReader();
        SortedDocValues fsModifiedValues = reader.getSortedDocValues(BasicProps.MODIFIED);

        List<IItemId> selectedItems = new ArrayList<IItemId>();
        List<Float> scores = new ArrayList<Float>();

        List<SortedSetDocValues> fieldValues = new ArrayList<SortedSetDocValues>();
        for (int i = 0; i < internalCreatedFieldName.size(); i++) {
            String field = internalCreatedFieldName.get(i);
            SortedSetDocValues internalValues = reader.getSortedSetDocValues(field);
            fieldValues.add(internalValues);
        }

        int i = -1;
        OUT: for (IItemId item : src.getIterator()) {
            i++;
            int docId = ipedCase.getLuceneId(item);

            if (fsModifiedValues.advanceExact(docId)) {
                int fsOrd = fsModifiedValues.ordValue();
                String fsStrDate = fsModifiedValues.lookupOrd(fsOrd).utf8ToString();
                if (fsStrDate.isEmpty()) {
                    continue;
                }
                for (SortedSetDocValues internalValues : fieldValues) {
                    if (internalValues != null && internalValues.advanceExact(docId)) {
                        long intOrd = internalValues.nextOrd();
                        while (intOrd != SortedSetDocValues.NO_MORE_ORDS) {
                            String intStrDate = internalValues.lookupOrd(intOrd).utf8ToString();
                            try {
                                Date fsDate = DateUtil.stringToDate(fsStrDate);
                                Date intDate = DateUtil.stringToDate(intStrDate);
                                if (Math.abs(fsDate.getTime() - intDate.getTime()) < PRODUCTION_TIMERANGE) {
                                    selectedItems.add(item);
                                    scores.add(src.getScore(i));
                                    continue OUT;
                                }
                            } catch (Exception e) {
                                // ignore
                            }

                            intOrd = internalValues.nextOrd();
                        }
                    }
                }
            }
        }

        return new MultiSearchResult(selectedItems.toArray(new ItemId[0]),
                ArrayUtils.toPrimitive(scores.toArray(new Float[0])));
    }

    public String toString() {
        return ProducedFilter.FILTER_NAME;
    }
}
