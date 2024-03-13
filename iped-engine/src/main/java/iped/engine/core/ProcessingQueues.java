package iped.engine.core;

import java.util.LinkedList;
import java.util.TreeMap;

import iped.data.IItem;
import iped.engine.data.CaseData;
import iped.engine.util.Util;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ProcessingQueues {

    private static final int QUEUE_SIZE = 100000;

    private TreeMap<Integer, LinkedList<IItem>> queues;

    private volatile Integer currentQueuePriority = 0;
    
    private CaseData caseData;

    private int maxQueueSize = QUEUE_SIZE;

    private int totalItemsBeingProcessed = 0;

    public ProcessingQueues(CaseData caseData) {
        this.caseData = caseData;
        initQueues();
    }

    private void initQueues() {
        queues = new TreeMap<Integer, LinkedList<IItem>>();
        queues.put(0, new LinkedList<IItem>());
        for (Integer priority : QueuesProcessingOrder.getProcessingQueues())
            queues.put(priority, new LinkedList<IItem>());
    }

    public void addItem(IItem item) throws InterruptedException {
        addItemToQueue(item, currentQueuePriority, false, true);
    }

    public void addItemFirst(IItem item) throws InterruptedException {
        addItemToQueue(item, currentQueuePriority, true, true);
    }

    public void addItemNonBlocking(IItem item) {
        try {
            addItemToQueue(item, currentQueuePriority, false, false);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void addItemFirstNonBlocking(IItem item) {
        try {
            addItemToQueue(item, currentQueuePriority, true, false);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void addItemToQueue(IItem item, int queuePriority) throws InterruptedException {
        addItemToQueue(item, queuePriority, false, false);
    }

    public void addItemToQueueSpaced(IItem item, int queuePriority, int numWorkers, AtomicInteger lastQueueIndex, AtomicLong lastQueueTime, int queueSplit, long queueDeltaTime) throws InterruptedException {
        addItemToQueueSpaced(item, queuePriority, true, numWorkers,  lastQueueIndex,  lastQueueTime, queueSplit,queueDeltaTime);
    }

    private void addItemToQueueSpaced(IItem item, int queuePriority, boolean blockIfFull, int numWorkers, AtomicInteger lastQueueIndex, AtomicLong lastQueueTime, int queueSplit, long queueDeltaTime)
            throws InterruptedException {

        Util.calctrackIDAndUpdateID(caseData, item);

        LinkedList<IItem> queue = queues.get(queuePriority);
        boolean sleep = false;
        while (true) {
            if (sleep) {
                sleep = false;
                Thread.sleep(1000);
            }
            synchronized (this) {
                if (blockIfFull && queuePriority == 0 && queue.size() >= maxQueueSize) {
                    sleep = true;
                    continue;
                } else {
                    queueSplit = (queueSplit <= 0)?4:queueSplit;
                    queueDeltaTime = (queueDeltaTime <= 0)?5000:queueDeltaTime;
                    int queueSplitInteger = (int)(queue.size()/queueSplit);
                    if (lastQueueIndex.get() == -1){
                        lastQueueIndex.set(queueSplitInteger);
                        lastQueueTime.set(System.currentTimeMillis());
                    }else{
                        if (lastQueueIndex.get() + numWorkers < queue.size() && ((System.currentTimeMillis() - lastQueueTime.get()) < queueDeltaTime)){
                            lastQueueIndex.addAndGet(numWorkers);
                        }else{                            
                            lastQueueIndex.set(queueSplitInteger);
                        }
                        lastQueueTime.set(System.currentTimeMillis());
                    }
                    queue.add(lastQueueIndex.get(),item);                   
                    break;
                }
            }
        }
    }

    private void addItemToQueue(IItem item, int queuePriority, boolean addFirst, boolean blockIfFull)
            throws InterruptedException {

        Util.calctrackIDAndUpdateID(caseData, item);

        LinkedList<IItem> queue = queues.get(queuePriority);
        boolean sleep = false;
        while (true) {
            if (sleep) {
                sleep = false;
                Thread.sleep(1000);
            }
            synchronized (this) {
                if (blockIfFull && queuePriority == 0 && queue.size() >= maxQueueSize) {
                    sleep = true;
                    continue;
                } else {
                    if (addFirst) {
                        queue.addFirst(item);
                    } else {
                        queue.addLast(item);
                    }
                    break;
                }
            }
        }

    }

    public synchronized int getItemsBeingProcessed() {
        return totalItemsBeingProcessed;
    }

    public synchronized void incItemsBeingProcessed() {
        totalItemsBeingProcessed++;
    }

    public synchronized void decItemsBeingProcessed() {
        totalItemsBeingProcessed--;
    }

    public synchronized boolean isNoItemInQueueOrBeingProcessed() {
        return totalItemsBeingProcessed == 0 && getItemQueue().size() == 0;
    }

    public synchronized IItem pollFirstFromCurrentQueue() throws InterruptedException {
        return getItemQueue().pollFirst();
    }

    public synchronized void addLastToCurrentQueue(IItem item) throws InterruptedException {
        getItemQueue().addLast(item);
    }

    public synchronized IItem peekItemFromCurrentQueue() {
        return getItemQueue().peek();
    }

    public synchronized int getCurrentQueueSize() {
        return getItemQueue().size();
    }

    public Integer changeToNextQueue() {
        currentQueuePriority = queues.ceilingKey(currentQueuePriority + 1);
        return currentQueuePriority;
    }

    public Integer getCurrentQueuePriority() {
        return currentQueuePriority;
    }

    /**
     * Obtém fila de arquivos de evidência do caso.
     *
     * @return fila de arquivos.
     */
    private LinkedList<IItem> getItemQueue() {
        return queues.get(currentQueuePriority);
    }

}
