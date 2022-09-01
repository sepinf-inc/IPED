package iped.viewers.timelinegraph.datasets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.util.BytesRef;

import iped.app.ui.App;
import iped.app.ui.CaseSearchFilterListener;
import iped.app.ui.CaseSearcherFilter;
import iped.app.ui.MetadataPanel;
import iped.app.ui.MetadataPanel.LookupOrd;
import iped.app.ui.MetadataPanel.ValueCount;
import iped.data.IItemId;
import iped.data.IMultiBookmarks;
import iped.engine.search.MultiSearchResult;
import iped.engine.search.MultiSearchResult.ItemIdIterator;
import iped.engine.task.index.IndexItem;
import iped.properties.ExtraProperties;
import iped.search.IMultiSearchResult;
import iped.viewers.api.CancelableWorker;
import iped.viewers.timelinegraph.datasets.IpedTimelineDataset.Accumulator;

public class LuceneFilterListener implements CaseSearchFilterListener{
	IpedTimelineDataset ipedTimelineDataset;
	String eventType;
	CaseSearcherFilter csf;
	String eventField;
	String bookmark;
	private SortedSetDocValues eventDocValuesSet;
    volatile HashMap<String, long[]> eventSetToOrdsCache = new HashMap<>();
	
	public LuceneFilterListener(String eventType, CaseSearcherFilter csf, IpedTimelineDataset ipedTimelineDataset, String bookmark) {
		this.ipedTimelineDataset = ipedTimelineDataset;
		this.eventType = eventType;
		this.eventField = ipedTimelineDataset.ipedChartsPanel.getTimeEventColumnName(eventType);
		this.bookmark=bookmark;
		this.csf = csf;
	}
	
	@Override
	public void onStart() {
	}

	int THREAD_SLICE_COUNT = 20000;
	
    SliceCounter[] scs=null;
    int slices;
	private boolean cancelled;
    
	public class SliceCounter extends CancelableWorker<Void, Void> {
		int start;
	    int valueCount[]=null;
	    ArrayList<Integer> docIds[];
	    ArrayList<IItemId> itemIds[];
	    Accumulator threadAccumulator;
	    boolean finished=false;
		protected boolean merged=false;
		
		public SliceCounter(int start) {
			this.start = start;
		}
		
		@Override
		public Void doInBackground() {
			try {
				threadAccumulator = ipedTimelineDataset.new Accumulator();

				IMultiSearchResult ipedResult = csf.get();
				LeafReader reader = ipedTimelineDataset.reader;

	        	if(isCancelled()) {
	        		return null;
	        	}
				SortedSetDocValues docValuesSet = reader.getSortedSetDocValues(eventField);

				if(isCancelled()) {
	        		return null;
	        	}
				eventDocValuesSet = reader.getSortedSetDocValues(ExtraProperties.TIME_EVENT_GROUPS);
	            
				SortedDocValues docValues=null;
	            LookupOrd lo;
				if(docValuesSet==null) {
		        	if(isCancelled()) {
		        		return null;
		        	}
					docValues = reader.getSortedDocValues(eventField);
					
					if(docValues==null) {
						System.out.println("Evento não contabilizado:"+eventField);
						return null;
					}
					lo = new MetadataPanel.LookupOrdSDV(docValues);
	            	valueCount=new int[(int)docValues.getValueCount()];
					docIds=new ArrayList[(int)docValues.getValueCount()];
					itemIds=new ArrayList[(int)docValues.getValueCount()];
				}else{
	            	valueCount=new int[(int)docValuesSet.getValueCount()];
	            	docIds=new ArrayList[(int)docValuesSet.getValueCount()];
					itemIds=new ArrayList[(int)docValuesSet.getValueCount()];
					lo = new MetadataPanel.LookupOrdSSDV(docValuesSet);
				}

		        IMultiBookmarks multiBookmarks = App.get().getIPEDSource().getMultiBookmarks();

				MultiSearchResult.ItemIdIterator iterator = (ItemIdIterator) ipedResult.getIterator();
				iterator.setPos(start);
				int count=0;
		        while(iterator.hasNext()) {
		        	if(isCancelled()) {
		        		return null;
		        	}
		        	if(count>=THREAD_SLICE_COUNT) {
		        		break;
		        	}
		        	count++;
		        	
		        	IItemId item = iterator.next();
		        	
					if(bookmark!=null && ipedTimelineDataset.ipedChartsPanel.getChartPanel().getSplitByBookmark()) {
		            	if(multiBookmarks.hasBookmark(item, bookmark)) {
		            		processItem(item, docValuesSet, docValues, valueCount, docIds, itemIds);
		            	}
					}else {
						processItem(item, docValuesSet, docValues, valueCount, docIds, itemIds);
					}
		        }
				
				for(int i=0;i<valueCount.length;i++) {
		        	if(isCancelled()) {
		        		return null;
		        	}
					if(valueCount[i]>0) {
						threadAccumulator.addValue(new ValueCount(lo, i, valueCount[i]),  docIds[i], itemIds[i], eventType);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				synchronized (scs) {
					finished=true;
					scs.notify();
				}
			}
			
			return null;
		}
	}

	public void processItem(IItemId item, SortedSetDocValues docValuesSet, SortedDocValues docValues, int[] valueCount, ArrayList<Integer>[] docIds, ArrayList<IItemId>[] itemIds) {
        int doc = App.get().appCase.getLuceneId(item);
        try {
    		if(docValuesSet != null) {
                boolean adv = docValuesSet.advanceExact(doc);
                long ord, prevOrd = -1;
                while (adv && (ord = docValuesSet.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
                    if (prevOrd != ord) {	                	
                        valueCount[(int) ord]++;
                        if(docIds[(int) ord]!=null) {
                        	itemIds[(int) ord].add(item);
                        	docIds[(int) ord].add(doc);
                        }else {
                        	itemIds[(int) ord]=new ArrayList<IItemId>();
                        	itemIds[(int) ord].add(item);
                        	docIds[(int) ord]=new ArrayList<Integer>();
                        	docIds[(int) ord].add(doc);
                        }
                    }
                    prevOrd = ord;
                }
    		}else {
                boolean adv = docValues.advanceExact(doc);
                int ord = (int) docValues.ordValue();
                valueCount[ord]++;
                if(docIds[(int) ord]!=null) {
                	itemIds[(int) ord].add(item);
                	docIds[(int) ord].add(doc);
                }else {
                	itemIds[(int) ord]=new ArrayList<IItemId>();
                	itemIds[(int) ord].add(item);
                	docIds[(int) ord]=new ArrayList<Integer>();
                	docIds[(int) ord].add(doc);
                }
    		}
        	
        }catch(Exception e) {
        	e.printStackTrace();        	
        }
        
	}

    private long[] getEventOrdsFromEventSet(SortedSetDocValues eventDocValues, String eventSet) throws IOException {
        long[] ords = eventSetToOrdsCache.get(eventSet);
        if (ords != null) {
            return ords;
        }
        String[] events = eventSet.split(Pattern.quote(IndexItem.EVENT_SEPARATOR));
        ords = new long[events.length];
        for (int i = 0; i < ords.length; i++) {
            long ord = eventDocValues.lookupTerm(new BytesRef(events[i]));
            ords[i] = ord;
        }
        eventSetToOrdsCache.put(eventSet, ords);
        return ords;
    }

	public void onDone() {
		try {
			IMultiSearchResult ipedResult = csf.getDoneResult();
			
			if(ipedResult.getLength()>0) {
				
				LeafReader reader = ipedTimelineDataset.reader;

				this.eventField = ipedTimelineDataset.ipedChartsPanel.getTimeEventColumnName(this.eventType);
	            int valueCount[]=null;
	            ArrayList<Integer> docIds[];
	            ArrayList<IItemId> itemIds[];

	            if(isCancelled()) {
	            	throw new InterruptedException();
	            }
				SortedSetDocValues docValuesSet = reader.getSortedSetDocValues(this.eventField);
	            SortedDocValues docValues=null;
	            LookupOrd lo;
				if(docValuesSet==null) {
					docValues = reader.getSortedDocValues(this.eventField);
					if(docValues==null) {
						System.out.println("Evento não contabilizado:"+eventField);
						return;
					}
					lo = new MetadataPanel.LookupOrdSDV(docValues);
					valueCount=new int[(int)docValues.getValueCount()];
					docIds=new ArrayList[(int)docValues.getValueCount()];
					itemIds=new ArrayList[(int)docValues.getValueCount()];
				}else{
					valueCount=new int[(int)docValuesSet.getValueCount()];
					docIds=new ArrayList[(int)docValuesSet.getValueCount()];
					itemIds=new ArrayList[(int)docValuesSet.getValueCount()];
					lo = new MetadataPanel.LookupOrdSSDV(docValuesSet);
				}
		        IMultiBookmarks multiBookmarks = App.get().getIPEDSource().getMultiBookmarks();

				MultiSearchResult.ItemIdIterator iterator = (ItemIdIterator) ipedResult.getIterator();
		        for (IItemId item : iterator) {
		            if(isCancelled()) {
		            	throw new InterruptedException();
		            }

					if(bookmark!=null && ipedTimelineDataset.ipedChartsPanel.getChartPanel().getSplitByBookmark()) {
		            	if(multiBookmarks.hasBookmark(item, bookmark)) {
		            		processItem(item, docValuesSet, docValues, valueCount, docIds, itemIds);
		            	}
					}else {
						processItem(item, docValuesSet, docValues, valueCount, docIds, itemIds);
					}
		        }
				
				for(int i=0;i<valueCount.length;i++) {
		            if(isCancelled()) {
		            	throw new InterruptedException();
		            }
					if(valueCount[i]>0) {
	    	        	ipedTimelineDataset.addValue(new ValueCount(lo, i, valueCount[i]), docIds[i], itemIds[i], eventType);
					}
				}
		            
			}

		} catch (Exception e) {
			if(!(e instanceof InterruptedException)) {
				e.printStackTrace();
			}
		}finally {
			ipedTimelineDataset.threadCountSem.release();
		}
	}

	private String getRealEventName(IMultiSearchResult result, String eventType) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onCancel(boolean mayInterruptIfRunning) {
		if(scs!=null) {
			for (int i = 0; i < scs.length; i++) {
				if(scs[i]!=null) {
					scs[i].cancel(mayInterruptIfRunning);
				}
			}
		}
		cancelled = true;
	}

	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void init() {
		
	}
}