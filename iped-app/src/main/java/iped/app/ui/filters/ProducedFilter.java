package iped.app.ui.filters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import org.apache.commons.lang.ArrayUtils;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;

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

    static HashSet<String> internalCreatedFieldName = new HashSet<String>();
    public static String FILTER_NAME = "Produced";
    
    public static int PRODUCTION_TIMERANGE = 60 * 1000;// 1 minute is the acceptable date difference between FS date and content date

    static {
        internalCreatedFieldName.add("common:dcterms:created");
        internalCreatedFieldName.add("image:Exif SubIFD:Date/Time Original");
        internalCreatedFieldName.add("image:Exif IFD0:Date/Time");
    }

    @Override
    public IMultiSearchResult filterResult(IMultiSearchResult src) throws ParseException, QueryNodeException, IOException {
        IPEDMultiSource ipedCase = (IPEDMultiSource) src.getIPEDSource();
        LeafReader reader = ipedCase.getLeafReader();
        SortedDocValues fsModifiedValues = reader.getSortedDocValues(BasicProps.MODIFIED);

        ArrayList<IItemId> selectedItems = new ArrayList<IItemId>();
        ArrayList<Float> scores = new ArrayList<Float>();

        int i = 0;

        HashMap<String, SortedSetDocValues> fieldValuesMap = new HashMap<String, SortedSetDocValues>();

        principal:
        for (IItemId item : src.getIterator()) {
            int docId = ipedCase.getLuceneId(item);

            if (fsModifiedValues.advanceExact(docId)) {
                int fsord = fsModifiedValues.ordValue();

                String fsdate = fsModifiedValues.lookupOrd(fsord).utf8ToString();

                for (Iterator iterator = internalCreatedFieldName.iterator(); iterator.hasNext();) {
                    String field = (String) iterator.next();
                    SortedSetDocValues internalValues = fieldValuesMap.get(field);
                    if (internalValues == null) {
                        internalValues = reader.getSortedSetDocValues(field);
                        if (internalValues != null) {
                            fieldValuesMap.put(field, internalValues);
                        }
                    }
                    if (internalValues != null) {
                        try {
                            if (internalValues.advanceExact(docId)) {
                                long intord = internalValues.nextOrd();
                                while (intord != internalValues.NO_MORE_ORDS) {
                                    String intdate = internalValues.lookupOrd(intord).utf8ToString();

                                    try {
                                        Date fsDate = DateUtil.stringToDate(fsdate);
                                        Date intDate = DateUtil.stringToDate(intdate);

                                        if (Math.abs(fsDate.getTime() - intDate.getTime()) < PRODUCTION_TIMERANGE) {
                                            selectedItems.add(item);
                                            scores.add(src.getScore(i));
                                            continue principal;
                                        }
                                    } catch (Exception e) {
                                        // ignore
                                    }

                                    intord = internalValues.nextOrd();
                                }
                            }
                        } catch (Exception e) {
                            continue;
                        }
                    }
                }
            }
            i++;

        }


        return new MultiSearchResult(selectedItems.toArray(new ItemId[0]), ArrayUtils.toPrimitive(scores.toArray(new Float[0])));
    }

    public String toString() {
        return ProducedFilter.FILTER_NAME;
    }
}
