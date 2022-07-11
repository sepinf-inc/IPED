package iped.viewers.timelinegraph.datasets;

import java.util.TreeMap;

import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.search.DocIdSetIterator;
import org.jfree.data.time.TimePeriod;

import iped.app.ui.CaseSearchFilterListener;
import iped.app.ui.CaseSearcherFilter;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.MultiSearchResult;
import iped.search.IMultiSearchResult;
import iped.viewers.timelinegraph.datasets.IpedTimelineDataset.Count;

public class LuceneFilterListener implements CaseSearchFilterListener{
	IpedTimelineDataset ipedTimelineDataset;
	String eventType;
	CaseSearcherFilter csf;
	String eventField;
	
	public LuceneFilterListener(String eventType, CaseSearcherFilter csf, IpedTimelineDataset ipedTimelineDataset) {
		this.ipedTimelineDataset = ipedTimelineDataset;
		this.eventType = eventType;
		this.eventField = ipedTimelineDataset.ipedChartsPanel.getTimeEventColumnName(eventType);
	}
	
	@Override
	public void onStart() {
		System.out.println("Searching:"+eventType);
	}

	@Override
	public void onDone() {
		try {
			IMultiSearchResult result = csf.get();
			if(result.getLength()>0) {
				DocIdSetIterator timeStampValues = ipedTimelineDataset.reader.getSortedDocValues(eventField);
				
				if(timeStampValues==null) {
					timeStampValues = ipedTimelineDataset.reader.getSortedSetDocValues(eventField);
				}
				
				int luceneId = timeStampValues.nextDoc();
				TimePeriod lastTimePeriod = null;
				TreeMap<TimePeriod,Count> timePeriodCount = null;
	            Count count = null;
	            boolean firstDoc = true;
				while(luceneId!=DocIdSetIterator.NO_MORE_DOCS) {
					MultiSearchResult mresult = (MultiSearchResult) result;
					if(((IPEDSearcher) csf.getSearcher()).hasDocId(luceneId)) {
						if(firstDoc) {
							timePeriodCount=new TreeMap<TimePeriod,Count>();
							synchronized (ipedTimelineDataset.eventTypesArray) {
								ipedTimelineDataset.eventTypesArray[ipedTimelineDataset.seriesCount]=eventType;
								ipedTimelineDataset.seriesCount++;
							}
							firstDoc = false;
						}
						TimePeriod t;
			            if(timeStampValues instanceof SortedDocValues) {
			            	SortedDocValues values = (SortedDocValues) timeStampValues;
			            	t = ipedTimelineDataset.ipedChartsPanel.getDomainAxis().getDateOnConfiguredTimePeriod(ipedTimelineDataset.ipedChartsPanel.getTimePeriodClass(), ipedTimelineDataset.ipedChartsPanel.getDomainAxis().ISO8601DateParse(values.lookupOrd(values.ordValue()).utf8ToString()));
							if(!t.equals(lastTimePeriod)) {
								count=timePeriodCount.get(t);
								if(count==null) {
					            	count=ipedTimelineDataset.new Count();
					            	synchronized (ipedTimelineDataset.values) {
					            		timePeriodCount.put(t, count);
					            		ipedTimelineDataset.values.addValue(count, t, eventType);
									}
								}else {
									count.value++;
								}
				            	lastTimePeriod=t;
							}else {
				            	count.value++;
							}
				            if(ipedTimelineDataset.first == null || t.getStart().before(ipedTimelineDataset.first)) {
				            	ipedTimelineDataset.first = t.getStart();
				            }
				            if(ipedTimelineDataset.last==null || t.getEnd().after(ipedTimelineDataset.last)) {
				            	ipedTimelineDataset.last = t.getEnd();
				            }
			            } else if(timeStampValues instanceof SortedSetDocValues) {
			            	SortedSetDocValues values = (SortedSetDocValues) timeStampValues;
			            	long ord=-1;
				            while ((ord = values.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
				            	String value = values.lookupOrd(ord).utf8ToString();
					            t = ipedTimelineDataset.ipedChartsPanel.getDomainAxis().getDateOnConfiguredTimePeriod(ipedTimelineDataset.ipedChartsPanel.getTimePeriodClass(), ipedTimelineDataset.ipedChartsPanel.getDomainAxis().ISO8601DateParse(value));
					            if(t!=null) {
									if(!t.equals(lastTimePeriod)) {
										count=timePeriodCount.get(t);
										if(count==null) {
							            	count=ipedTimelineDataset.new Count();
							            	synchronized (ipedTimelineDataset.values) {
							            		timePeriodCount.put(t, count);
							            		ipedTimelineDataset.values.addValue(count, t, eventType);
											}
										}else {
											count.value++;
										}
						            	lastTimePeriod=t;
									}else {
						            	count.value++;
									}
						            if(ipedTimelineDataset.first == null || t.getStart().before(ipedTimelineDataset.first)) {
						            	ipedTimelineDataset.first = t.getStart();
						            }
						            if(ipedTimelineDataset.last==null || t.getEnd().after(ipedTimelineDataset.last)) {
						            	ipedTimelineDataset.last = t.getEnd();
						            }
					            }else {
					            	System.out.println("erro: "+value);
					            	continue;
					            }
				            }
			            }
					}
					luceneId = timeStampValues.nextDoc();
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