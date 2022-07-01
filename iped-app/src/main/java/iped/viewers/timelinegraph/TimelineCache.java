package iped.viewers.timelinegraph;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;

import javax.swing.SwingWorker;

import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

import iped.app.ui.App;
import iped.data.IItemId;
import iped.engine.lucene.DocValuesUtil;
import iped.engine.search.IPEDSearcher;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IMultiSearchResult;
import iped.viewers.api.IMultiSearchResultProvider;

public class TimelineCache extends SwingWorker<Void, Void> {
	IMultiSearchResultProvider resultsProvider;
	Object[][] itemEvents = null;
	HashMap<Integer,String> events = new HashMap<Integer,String>();

	public TimelineCache(IMultiSearchResultProvider resultsProvider) {
		this.resultsProvider = resultsProvider;
	}

	public class ItemEvent{
		int eventCode;
		Date date;
		
		public String getEventString() {
			return events.get(eventCode);
		}
	}

	@Override
	protected Void doInBackground() throws Exception {
		createCache();
		return null;
	}
	
	public List<ItemEvent> getEvents(IItemId itemId){
		try {
			return ((List<ItemEvent>) itemEvents[itemId.getSourceId()][itemId.getId()]);
		}catch(Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public void createCache(){
		try {
			while(App.get()==null || App.get().appCase==null) {
				Thread.sleep(100);
			}

			IPEDSearcher searcher = new IPEDSearcher(App.get().appCase, "");
	        IMultiSearchResult sourceSearchResults = searcher.multiSearch();
	        LeafReader reader = resultsProvider.getIPEDSource().getLeafReader();

	        SortedSetDocValues timeStampValues;
	        SortedSetDocValues timeEventGroupValues;

	        int[] eventOrd = new int[Short.MAX_VALUE];
	        int[][] eventsInDocOrds = new int[Short.MAX_VALUE][1 << 9];
	        
	        itemEvents = new Object[Short.MAX_VALUE][];

	        timeStampValues = reader.getSortedSetDocValues(BasicProps.TIMESTAMP);
			timeEventGroupValues = reader.getSortedSetDocValues(ExtraProperties.TIME_EVENT_GROUPS);
			BinaryDocValues eventsInDocOrdsValues = reader.getBinaryDocValues(ExtraProperties.TIME_EVENT_ORDS);
			
			TermsEnum eventTypes = timeEventGroupValues.termsEnum();
			BytesRef eventTypeBR = eventTypes.next();
			int i=0;
			HashSet<String> eventsTest = new HashSet<String>();
			while(eventTypeBR!=null) {
				String eventTypesStr = eventTypeBR.utf8ToString();
           		StringTokenizer st = new StringTokenizer(eventTypesStr, "|");
           		if(st.hasMoreTokens()) {
           			String eventType = st.nextToken().trim();
           			if(!eventsTest.contains(eventType)) {
           				eventsTest.add(eventType);
        				events.put(i, eventType);
        				i++;
           			}
           		}
				eventTypeBR = eventTypes.next();
			}

			if(sourceSearchResults.getLength()>0) {
		        for (IItemId item : sourceSearchResults.getIterator()) {
		            int luceneId = resultsProvider.getIPEDSource().getLuceneId(item);

		            Object[] eventsArray = itemEvents[item.getSourceId()];
		            if(eventsArray==null) {
						itemEvents[item.getSourceId()] = new Object[sourceSearchResults.getLength()*2];
						eventsArray = itemEvents[item.getSourceId()];
		            }
		            
		            String eventsInDocStr = DocValuesUtil.getVal(eventsInDocOrdsValues, luceneId);
		            if (eventsInDocStr.isEmpty()) {
		                continue;
		            }

		            boolean tsvAdv = timeStampValues.advanceExact(luceneId);
		            boolean tegvAdv = timeEventGroupValues.advanceExact(luceneId);

		            long ord;
		            short pos = 0;
		            while (tegvAdv && (ord = timeEventGroupValues.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
		                for (int k : eventsInDocOrds[pos++]) {
		                    if (k == -1) {
		                        break;
		                    }
		                    eventOrd[k] = (int) ord;
		                }
		            }
		            pos = 0;
		            while (tsvAdv && (ord = timeStampValues.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
		                if (ord > Integer.MAX_VALUE) {
		                    throw new RuntimeException("Integer overflow when converting timestamp ord to int");
		                }

	               		String itemEvents = timeEventGroupValues.lookupOrd(eventOrd[pos++]).utf8ToString();
	               		StringTokenizer st = new StringTokenizer(itemEvents, "|");
	               		if(eventsArray[item.getId()]==null) {
	               			eventsArray[item.getId()]=new ArrayList<ItemEvent>();
	               		}
	               		while(st.hasMoreTokens()) {
	               			String event = st.nextToken().trim();
	               			int eventCode = 0;
	               			for(Integer ec : events.keySet()) {
	               				String curEvent = events.get(ec);
	               				if(curEvent.equals(event)) {
	               					eventCode = ec;
	               					break;
	               				}
	               			}
	               			ItemEvent ie = new ItemEvent();
	               			ie.eventCode=eventCode;
	               			ie.date=DateUtil.ISO8601DateParse(timeStampValues.lookupOrd(ord).utf8ToString());
	               			((List<ItemEvent>) eventsArray[item.getId()]).add(ie);
	               		}
		            }
           		}
	        }
		}catch(Exception e) {
			e.printStackTrace();
		}
	}

}
