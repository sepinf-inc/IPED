package iped.app.timelinegraph.cache;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.UnicodeUtil;
import org.jfree.data.time.Day;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Month;
import org.jfree.data.time.Quarter;
import org.jfree.data.time.TimePeriod;
import org.jfree.data.time.Week;
import org.jfree.data.time.Year;

import iped.app.timelinegraph.DateUtil;
import iped.app.timelinegraph.IpedChartsPanel;
import iped.viewers.api.IMultiSearchResultProvider;

public class EventTimestampCache implements Runnable {
    String eventType;

    IMultiSearchResultProvider resultsProvider;
    TimeStampCache timeStampCache;
    IpedChartsPanel ipedChartsPanel;
    int emptyOrd;

    private Integer eventInternalOrd;

    static HashSet<Integer> done = new HashSet<>();

    public EventTimestampCache(IpedChartsPanel ipedChartsPanel, IMultiSearchResultProvider resultsProvider, TimeStampCache timeStampCache, String eventType, int ord) {
        this.eventType = eventType;
        this.eventInternalOrd = ord;
        this.resultsProvider = resultsProvider;
        this.timeStampCache = timeStampCache;
        this.ipedChartsPanel = ipedChartsPanel;
    }

    public void run() {
        LeafReader reader = resultsProvider.getIPEDSource().getLeafReader();

        IndexTimeStampCache timeStampCache = (IndexTimeStampCache) this.timeStampCache;

        DocIdSetIterator timeStampValues;
        try {
            String eventField = ipedChartsPanel.getTimeEventColumnName(eventType).trim();
            if (eventField != null) {
                timeStampValues = reader.getSortedDocValues(eventField);
                if (timeStampValues == null) {
                    SortedSetDocValues values = reader.getSortedSetDocValues(eventField);
                    TermsEnum tenum = values.termsEnum();
                    Long emptyValueOrd = getEmptyOrd(tenum);
                    if (emptyValueOrd == null) {
                        tenum = values.termsEnum();
                    }
                    Map<String, long[]> parsedDateCache = getParsedCache(tenum, (int) values.getValueCount());

                    int doc = values.nextDoc();
                    while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                        int ord = (int) values.nextOrd();
                        while (ord != SortedSetDocValues.NO_MORE_ORDS) {
                            if (emptyValueOrd == null || ord != emptyValueOrd) {
                                for (Class<? extends TimePeriod> timePeriodClass : timeStampCache.getPeriodClassesToCache()) {
                                    Date date = null;
                                    long[] cache = parsedDateCache.get(timePeriodClass.getSimpleName());
                                    if (cache == null) {
                                        date = DateUtil.ISO8601DateParse(timePeriodClass, values.lookupOrd(ord).bytes);
                                    } else {
                                        date = new Date(cache[ord]);
                                    }
                                    if (date != null) {
                                        timeStampCache.add(timePeriodClass, date, eventInternalOrd, doc);
                                    }
                                }
                            }
                            ord = (int) values.nextOrd();
                        }
                        doc = values.nextDoc();
                    }
                } else {
                    SortedDocValues values = (SortedDocValues) timeStampValues;
                    TermsEnum tenum = values.termsEnum();
                    Long emptyValueOrd = getEmptyOrd(tenum);
                    if (emptyValueOrd == null) {
                        tenum = values.termsEnum();
                    }
                    Map<String, long[]> parsedDateCache = getParsedCache(tenum, values.getValueCount());

                    int doc = values.nextDoc();
                    while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                        int ord = values.ordValue();
                        if (emptyValueOrd == null || ord != emptyValueOrd) {
                            for (Class<? extends TimePeriod> timePeriodClass : timeStampCache.getPeriodClassesToCache()) {
                                Date date = null;
                                long[] cache = parsedDateCache.get(timePeriodClass.getSimpleName());
                                if (cache == null) {
                                    date = DateUtil.ISO8601DateParse(timePeriodClass, values.lookupOrd(ord).bytes);
                                } else {
                                    date = new Date(cache[ord]);
                                }
                                if (date != null) {
                                    timeStampCache.add(timePeriodClass, date, eventInternalOrd, doc);
                                }
                            }
                        }
                        doc = values.nextDoc();
                    }
                }
            }
            done.add(eventInternalOrd);

        } catch (AlreadyClosedException e) {
            // IPED closing before cache creation can throw this exception. So, we avoid
            // logging it.
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (timeStampCache.running.decrementAndGet() == 0) {
                synchronized (timeStampCache.monitor) {
                    timeStampCache.monitor.notifyAll();
                }
            }
        }
    }

    private Long getEmptyOrd(TermsEnum lenum) throws IOException {
        long[] a = new long[1];

        BytesRef bref = lenum.next();

        long ord = lenum.ord();
        if (bref.bytes.length <= 0) {
            return ord;
        }

        return null;
    }

    private Map<String, long[]> getParsedCache(TermsEnum lenum, int count) throws IOException {
        HashMap<String, long[]> result = new HashMap<String, long[]>();
        for (Class<? extends TimePeriod> timePeriodClass : timeStampCache.getPeriodClassesToCache()) {
            long[] a = new long[count];

            BytesRef bref = lenum.next();

            while (bref != null) {
                long ord = lenum.ord();
                if (bref.bytes.length > 10) {
                    Date date = DateUtil.ISO8601DateParse(timePeriodClass, bref.bytes);
                    a[(int) ord] = date.getTime();
                }

                bref = lenum.next();
            }
            result.put(timePeriodClass.getSimpleName(), a);
        }

        return result;
    }

    private static final boolean isPeriodToCache(Class<? extends TimePeriod> timePeriodClass) {
        return timePeriodClass == Year.class || timePeriodClass == Quarter.class || timePeriodClass == Month.class || timePeriodClass == Week.class || timePeriodClass == Day.class || timePeriodClass == Hour.class;
    }

    static public synchronized String cloneBr(BytesRef br) {
        char[] saida = new char[br.length];
        final int len = UnicodeUtil.UTF8toUTF16(br.bytes, br.offset, br.length, saida);
        return new String(saida, 0, len);
    }

    public class LRUCache<K, V> {
        // Define Node with pointers to the previous and next items and a key, value
        // pair
        class Node<T, U> {
            Node<T, U> previous;
            Node<T, U> next;
            T key;
            U value;

            public Node(Node<T, U> previous, Node<T, U> next, T key, U value) {
                this.previous = previous;
                this.next = next;
                this.key = key;
                this.value = value;
            }
        }

        private HashMap<K, Node<K, V>> cache;
        private Node<K, V> leastRecentlyUsed;
        private Node<K, V> mostRecentlyUsed;
        private int maxSize;
        private int currentSize;

        public LRUCache(int maxSize) {
            this.maxSize = maxSize;
            this.currentSize = 0;
            leastRecentlyUsed = new Node<K, V>(null, null, null, null);
            mostRecentlyUsed = leastRecentlyUsed;
            cache = new HashMap<K, Node<K, V>>();
        }

        public V get(K key) {
            Node<K, V> tempNode = cache.get(key);
            if (tempNode == null) {
                return null;
            }
            // If MRU leave the list as it is
            else if (tempNode.key == mostRecentlyUsed.key) {
                return mostRecentlyUsed.value;
            }

            // Get the next and previous nodes
            Node<K, V> nextNode = tempNode.next;
            Node<K, V> previousNode = tempNode.previous;

            // If at the left-most, we update LRU
            if (tempNode.key == leastRecentlyUsed.key) {
                nextNode.previous = null;
                leastRecentlyUsed = nextNode;
            }

            // If we are in the middle, we need to update the items before and after our
            // item
            else if (tempNode.key != mostRecentlyUsed.key) {
                previousNode.next = nextNode;
                nextNode.previous = previousNode;
            }

            // Finally move our item to the MRU
            tempNode.previous = mostRecentlyUsed;
            mostRecentlyUsed.next = tempNode;
            mostRecentlyUsed = tempNode;
            mostRecentlyUsed.next = null;

            return tempNode.value;

        }

        public void put(K key, V value) {
            if (cache.containsKey(key)) {
                return;
            }

            // Put the new node at the right-most end of the linked-list
            Node<K, V> myNode = new Node<K, V>(mostRecentlyUsed, null, key, value);
            mostRecentlyUsed.next = myNode;
            cache.put(key, myNode);
            mostRecentlyUsed = myNode;

            // Delete the left-most entry and update the LRU pointer
            if (currentSize == maxSize) {
                cache.remove(leastRecentlyUsed.key);
                leastRecentlyUsed = leastRecentlyUsed.next;
                leastRecentlyUsed.previous = null;
            }

            // Update cache size, for the first added entry update the LRU pointer
            else if (currentSize < maxSize) {
                if (currentSize == 0) {
                    leastRecentlyUsed = myNode;
                }
                currentSize++;
            }
        }
    }
}
