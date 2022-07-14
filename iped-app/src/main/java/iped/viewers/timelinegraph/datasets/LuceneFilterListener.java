package iped.viewers.timelinegraph.datasets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
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
    
	public class SliceCounter implements Runnable{
		int start;
	    int valueCount[]=null;
	    ArrayList<IItemId> docIds[];
	    Accumulator threadAccumulator;
	    boolean finished=false;
		protected boolean merged=false;
		
		public SliceCounter(int start) {
			this.start = start;
		}
		
		@Override
		public void run() {
			try {
				threadAccumulator = ipedTimelineDataset.new Accumulator();

				IMultiSearchResult ipedResult = csf.get();
				LeafReader reader = ipedTimelineDataset.reader;

				SortedSetDocValues docValuesSet = reader.getSortedSetDocValues(eventField);
				eventDocValuesSet = reader.getSortedSetDocValues(ExtraProperties.TIME_EVENT_GROUPS);
	            SortedDocValues docValues=null;
	            LookupOrd lo;
				if(docValuesSet==null) {
					docValues = reader.getSortedDocValues(eventField);
					if(docValues==null) {
						System.out.println("Evento não contabilizado:"+eventField);
						return;
					}
					lo = new MetadataPanel.LookupOrdSDV(docValues);
	            	valueCount=new int[(int)docValues.getValueCount()];
					docIds=new ArrayList[(int)docValues.getValueCount()];
				}else{
	            	valueCount=new int[(int)docValuesSet.getValueCount()];
	            	docIds=new ArrayList[(int)docValuesSet.getValueCount()];
					lo = new MetadataPanel.LookupOrdSSDV(docValuesSet);
				}

		        IMultiBookmarks multiBookmarks = App.get().getIPEDSource().getMultiBookmarks();

				MultiSearchResult.ItemIdIterator iterator = (ItemIdIterator) ipedResult.getIterator();
				iterator.setPos(start);
				int count=0;
		        while(iterator.hasNext()) {
		        	if(count>=THREAD_SLICE_COUNT) {
		        		break;
		        	}
		        	count++;
		        	
		        	IItemId item = iterator.next();
		        	
					if(bookmark!=null && ipedTimelineDataset.ipedChartsPanel.getChartPanel().getSplitByBookmark()) {
		            	if(multiBookmarks.hasBookmark(item, bookmark)) {
		            		process(item, docValuesSet, docValues, valueCount, docIds);
		            	}
					}else {
	            		process(item, docValuesSet, docValues, valueCount, docIds);
					}
		        }
				
				for(int i=0;i<valueCount.length;i++) {
					if(valueCount[i]>0) {
						threadAccumulator.addValue(new ValueCount(lo, i, valueCount[i]), docIds[i], eventType);
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
		}
	}

	public void process(IItemId item, SortedSetDocValues docValuesSet, SortedDocValues docValues, int[] valueCount, ArrayList<IItemId>[] docIds) {
        int doc = App.get().appCase.getLuceneId(item);
        try {
    		if(docValuesSet != null) {
                boolean adv = docValuesSet.advanceExact(doc);
                long ord, prevOrd = -1;
                while (adv && (ord = docValuesSet.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
                    if (prevOrd != ord) {	                	
                        valueCount[(int) ord]++;
                        if(docIds[(int) ord]!=null) {
                        	docIds[(int) ord].add(item);
                        }else {
                        	docIds[(int) ord]=new ArrayList<IItemId>();
                        	docIds[(int) ord].add(item);
                        }
                    }
                    prevOrd = ord;
                }
    		}else {
                boolean adv = docValues.advanceExact(doc);
                int ord = (int) docValues.ordValue();
                valueCount[ord]++;
                if(docIds[(int) ord]!=null) {
                	docIds[(int) ord].add(item);
                }else {
                	docIds[(int) ord]=new ArrayList<IItemId>();
                	docIds[(int) ord].add(item);
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

	public void onDone2() {
		try {
			IMultiSearchResult ipedResult = csf.get();
			
			eventField = ipedTimelineDataset.ipedChartsPanel.getTimeEventColumnName(eventType);

			if(ipedResult.getLength()>0) {
				
				THREAD_SLICE_COUNT = 10000;
				int countslices = (int) Math.ceil((double)ipedResult.getLength()/(double)THREAD_SLICE_COUNT);
				slices = countslices;
				scs = new SliceCounter[slices];
				
				for(int i=0; i<countslices; i++) {
					scs[i] = new SliceCounter(i*THREAD_SLICE_COUNT);
				}
				
				ExecutorService threadPool = csf.getThreadPool();

				Thread consumer = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							synchronized (scs) {
								while(slices>0) {
									scs.wait();

									for(int i=0; i<countslices; i++) {
										if(scs[i].finished && !scs[i].merged) {
											ipedTimelineDataset.accumulator.merge(scs[i].threadAccumulator);
											scs[i].merged=true;
											slices--;//consumed countdown
										}
									}
								}
							}
						}catch (Exception e) {
							e.printStackTrace();
						}finally {
							ipedTimelineDataset.running--;
							if(ipedTimelineDataset.running==0) {
								synchronized (ipedTimelineDataset.monitor) {
									ipedTimelineDataset.monitor.notifyAll(); 
								}
					        }
						}
					}
				});
				consumer.start();

				for(int i=0; i<countslices; i++) {
					threadPool.execute(scs[i]);//producers
				}
				
			}else {
				ipedTimelineDataset.running--;
				if(ipedTimelineDataset.running==0) {
					synchronized (ipedTimelineDataset.monitor) {
						ipedTimelineDataset.monitor.notifyAll(); 
					}
		        }
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void onDone() {
		try {
			IMultiSearchResult ipedResult = csf.get();
			
			if(ipedResult.getLength()>0) {
				LeafReader reader = ipedTimelineDataset.reader;

				this.eventField = ipedTimelineDataset.ipedChartsPanel.getTimeEventColumnName(this.eventType);
	            int valueCount[]=null;
	            ArrayList<IItemId> docIds[];

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
				}else{
					valueCount=new int[(int)docValuesSet.getValueCount()];
					docIds=new ArrayList[(int)docValuesSet.getValueCount()];
					lo = new MetadataPanel.LookupOrdSSDV(docValuesSet);
				}
		        IMultiBookmarks multiBookmarks = App.get().getIPEDSource().getMultiBookmarks();

				MultiSearchResult.ItemIdIterator iterator = (ItemIdIterator) ipedResult.getIterator();
		        for (IItemId item : iterator) {
					if(bookmark!=null && ipedTimelineDataset.ipedChartsPanel.getChartPanel().getSplitByBookmark()) {
		            	if(multiBookmarks.hasBookmark(item, bookmark)) {
		            		process(item, docValuesSet, docValues, valueCount, docIds);
		            	}
					}else {
	            		process(item, docValuesSet, docValues, valueCount, docIds);
					}
		        }
				
				for(int i=0;i<valueCount.length;i++) {
					if(valueCount[i]>0) {
	    	        	ipedTimelineDataset.addValue(new ValueCount(lo, i, valueCount[i]), docIds[i], eventType);						
					}
				}
		            
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally {
			ipedTimelineDataset.running--;
			if(ipedTimelineDataset.running==0) {
				synchronized (ipedTimelineDataset.monitor) {
					ipedTimelineDataset.monitor.notifyAll(); 
				}
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