package iped.app.timelinegraph.swingworkers;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.entity.EntityCollection;
import org.jfree.chart.entity.XYItemEntity;
import org.roaringbitmap.RoaringBitmap;

import iped.app.timelinegraph.IpedDateAxis;
import iped.app.timelinegraph.datasets.IpedTimelineDataset;
import iped.data.IItemId;
import iped.engine.data.IPEDSource;
import iped.engine.search.MultiSearchResult;
import iped.viewers.api.IMultiSearchResultProvider;

/*
 *  Extends BitSetHighlightWorker, so the bitset of docids is mounted based on date interval. This bitset is used internally to highlight the docids.
 */
public class HighlightWorker extends BitSetHighlightWorker {
    Date start;
    Date end;

    public HighlightWorker(IpedDateAxis domainAxis, IMultiSearchResultProvider resultsProvider, Date start, Date end, boolean clearPreviousHighlighted) {
        this(domainAxis, resultsProvider, start, end, true, clearPreviousHighlighted);
    }

    public HighlightWorker(IpedDateAxis domainAxis, IMultiSearchResultProvider resultsProvider, Date start, Date end, boolean highlight, boolean clearPreviousHighlighted) {
        super(domainAxis, resultsProvider, null, highlight, clearPreviousHighlighted);
        this.start = start;
        this.end = end;
    }

    @Override
    protected void doProcess() {
        highlightItemsOnInterval(start, end, highlight, clearPreviousItemsHighlighted);
    }

    public void highlightItemsOnInterval(Date start, Date end, boolean select, boolean clearPreviousSelection) {
        Date d1 = new Date();

        ArrayList<IItemId> l = new ArrayList<IItemId>();
        RoaringBitmap bs = new RoaringBitmap();

        progressDialog.setMaximum(resultsProvider.getResults().getLength());
        MultiSearchResult msr = (MultiSearchResult) resultsProvider.getResults();
        IPEDSource is = (IPEDSource) resultsProvider.getIPEDSource();

        EntityCollection ec = domainAxis.getIpedChartsPanel().getChartPanel().getChartRenderingInfo().getEntityCollection();
        int entityCount = ec.getEntityCount();
        for (int i = entityCount - 1; i >= 0; i--) {
            if (progressDialog.isCanceled()) {
                return;
            }
            ChartEntity entity = ec.getEntity(i);
            if (entity instanceof XYItemEntity) {
                XYItemEntity xyItemEntity = (XYItemEntity) entity;
                IpedTimelineDataset ds = (IpedTimelineDataset) xyItemEntity.getDataset();
                long entStartTimeStamp = ds.getStartX(xyItemEntity.getSeriesIndex(), xyItemEntity.getItem()).longValue();
                if (entStartTimeStamp >= start.getTime() && entStartTimeStamp <= end.getTime()) {
                    List<IItemId> ids = ds.getItems(xyItemEntity.getItem(), xyItemEntity.getSeriesIndex());
                    if (ids != null) {
                        for (Iterator iterator = ids.iterator(); iterator.hasNext();) {
                            IItemId iItemId = (IItemId) iterator.next();
                            bs.add(is.getLuceneId(iItemId));
                        }
                    }
                }
            }
        }

        highlightDocIdsParallel(bs, select, clearPreviousSelection);
    }
}
