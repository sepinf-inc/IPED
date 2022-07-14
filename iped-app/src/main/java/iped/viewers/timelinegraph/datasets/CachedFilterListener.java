package iped.viewers.timelinegraph.datasets;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import org.jfree.data.time.TimePeriod;

import iped.engine.data.IPEDMultiSource;
import iped.engine.data.ItemId;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.MultiSearchResult;
import iped.search.IMultiSearchResult;
import iped.viewers.timelinegraph.IpedChartPanel;
import iped.viewers.timelinegraph.datasets.IpedTimelineDataset.Count;
import iped.app.ui.App;
import iped.app.ui.CaseSearchFilterListener;
import iped.app.ui.CaseSearcherFilter;
import iped.data.IIPEDSource;
import iped.data.IItemId;
import iped.data.IMultiBookmarks;


public class CachedFilterListener implements CaseSearchFilterListener{
	IpedTimelineDataset ipedTimelineDataset;
	String eventType;
	CaseSearcherFilter csf;
	String bookmark;
	String eventField;
	private IpedChartPanel chartPanel;
	
	public CachedFilterListener(String eventType, CaseSearcherFilter csf, IpedTimelineDataset ipedTimelineDataset, String bookmark) {
		this.ipedTimelineDataset = ipedTimelineDataset;
		this.eventType = eventType;
		this.eventField = ipedTimelineDataset.ipedChartsPanel.getTimeEventColumnName(eventType);
		this.chartPanel = ipedTimelineDataset.ipedChartsPanel.getChartPanel();
		this.bookmark=bookmark;
		this.csf = csf;
	}
	
	@Override
	public void onStart() {
	}

	@Override
	public void onDone() {
		try {
			IMultiSearchResult result = csf.get();
			App app = App.get();
	        IMultiBookmarks multiBookmarks = App.get().getIPEDSource().getMultiBookmarks();
	        IPEDMultiSource appcase = (IPEDMultiSource) app.getIPEDSource();
	        
			if(result.getLength()>0) {
				Map<TimePeriod, ArrayList<Integer>> timeStampDocs = ipedTimelineDataset.ipedChartsPanel.getIpedTimelineDatasetManager().getCachedEventTimeStamps(eventType);

				for(TimePeriod t:timeStampDocs.keySet()) {
					ArrayList<Integer> docs = timeStampDocs.get(t);
					ArrayList<IItemId> includedDocs = new ArrayList<IItemId>();
					Count count=ipedTimelineDataset.new Count();
					for(Integer docId:docs) {
						MultiSearchResult mresult = (MultiSearchResult) result;
	                    IIPEDSource atomicSource = appcase.getAtomicSource(docId);
	                    int sourceId = atomicSource.getSourceId();
	                    int baseDoc = appcase.getBaseLuceneId(atomicSource);
	                    ItemId ii = new ItemId(sourceId, atomicSource.getId(docId - baseDoc));
						
						if(((IPEDSearcher) csf.getSearcher()).hasDocId(docId)) {
							if(bookmark!=null && chartPanel.getSplitByBookmark()) {
				            	if(multiBookmarks.hasBookmark(ii, bookmark)) {
									count.value++;
									includedDocs.add(ii);
			            	    }
							}else {
								count.value++;
								includedDocs.add(ii);
							}
						}
					}
					if(count.value>0) {
						ipedTimelineDataset.addValue(count, t, eventField, includedDocs);
					}
				}
			}
		} catch (Exception e) {
			System.out.println(eventType);
			e.printStackTrace();
		}
		ipedTimelineDataset.running--;
		if(ipedTimelineDataset.running==0) {
			synchronized (ipedTimelineDataset.monitor) {
				ipedTimelineDataset.monitor.notifyAll(); 
			}
        }
	}

	private String getRealEventName(IMultiSearchResult result, String eventType) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onCancel(boolean mayInterruptIfRunning) {
	}

	@Override
	public void init() {
	}
}