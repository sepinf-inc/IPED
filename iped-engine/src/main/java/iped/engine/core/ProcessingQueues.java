package iped.engine.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.SplittableRandom;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.ProcessingPriorityConfig;
import iped.engine.data.CaseData;
import iped.engine.util.Util;

public class ProcessingQueues {
    /*
     The LinkedList's are used for prioritized items, inserted on the top of the
     queue. They can be used for the rest of the items if the selected behavior is
     the same used before #2541 (FIFO). The ArrayList's are used for the rest of
     the items, if random order is enabled (default). In such case, items are
     selected randomly from the active queue, to "spread" items, minimizing the
     situation of having all workers dealing with similar items, that would
     compete for the same type of resources.
     */
    private TreeMap<Integer, LinkedList<IItem>> queuesTop;
    private TreeMap<Integer, ArrayList<IItem>> queuesRest;

    private volatile Integer currentQueuePriority = 0;

    private CaseData caseData;

    private int maxQueueSize;
    private boolean randomOrder;

    private static Logger logger = LogManager.getLogger(ProcessingQueues.class);

    private int totalItemsBeingProcessed = 0;

    private final SplittableRandom rnd = new SplittableRandom();

    public ProcessingQueues(CaseData caseData) {
        this.caseData = caseData;
        initConfig();
        initQueues();
    }

    private void initConfig() {
        ProcessingPriorityConfig config = ConfigurationManager.get().findObject(ProcessingPriorityConfig.class);
        randomOrder = config.isRandomOrder();
        maxQueueSize = config.getMaxQueueSize();
        boolean auto = false;
        if (maxQueueSize == 0) {
            // If the queue maximum size is not explicitly defined, set it based on the
            // maximum heap size. 1 item each 64 KB, so 32 GB would allow ~500,000 items.
            maxQueueSize = (int) (Runtime.getRuntime().maxMemory() >>> 16);
            auto = true;
        }
        // Enforce a very minimal size
        maxQueueSize = Math.max(1024, maxQueueSize);

        logger.info("Maximum Processing Queue Size: {}{}", maxQueueSize, auto ? " (auto)" : "");
        logger.info("Processing Queue Random Order: {}", randomOrder ? "enabled" : "disabled");
    }

    private void initQueues() {
        queuesTop = new TreeMap<Integer, LinkedList<IItem>>();
        queuesTop.put(0, new LinkedList<IItem>());
        queuesRest = new TreeMap<Integer, ArrayList<IItem>>();
        queuesRest.put(0, new ArrayList<IItem>());
        for (Integer priority : QueuesProcessingOrder.getProcessingQueues()) {
            queuesTop.put(priority, new LinkedList<IItem>());
            queuesRest.put(priority, new ArrayList<IItem>());
        }
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

    private void addItemToQueue(IItem item, int queuePriority, boolean addFirst, boolean blockIfFull)
            throws InterruptedException {

        Util.calctrackIDAndUpdateID(caseData, item);

        LinkedList<IItem> q1 = queuesTop.get(queuePriority);
        ArrayList<IItem> q2 = queuesRest.get(queuePriority);
        boolean sleep = false;
        while (true) {
            if (sleep) {
                sleep = false;
                Thread.sleep(1000);
            }
            synchronized (this) {
                if (blockIfFull && queuePriority == 0 && q1.size() + q2.size() >= maxQueueSize) {
                    sleep = true;
                    continue;
                } else {
                    if (addFirst) {
                        q1.addFirst(item);
                    } else {
                        if (randomOrder) {
                            q2.add(item);
                        } else {
                            // If "random order" is disabled, FIFO is used, and new items are placed in the
                            // end of the LinkedList queue.
                            q1.addLast(item);
                        }
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
        return totalItemsBeingProcessed == 0 && getItemQueueTop().isEmpty() && getItemQueueRest().isEmpty();
    }

    public synchronized IItem pollFromCurrentQueue() throws InterruptedException {
        LinkedList<IItem> q1 = getItemQueueTop();
        if (!q1.isEmpty()) {
            return q1.pollFirst();
        }
        ArrayList<IItem> q2 = getItemQueueRest();
        if (q2.size() > 2) {
            int pos = rnd.nextInt(q2.size() - 1);
            IItem item = q2.get(pos);
            Collections.swap(q2, pos, q2.size() - 2);
            q2.remove(q2.size() - 2);
            return item;
        }
        return q2.isEmpty() ? null : q2.remove(0);
    }

    public synchronized void addToCurrentQueue(IItem item) throws InterruptedException {
        getItemQueueRest().add(item);
    }

    public synchronized IItem peekItemFromCurrentQueue() {
        return getItemQueueTop().isEmpty() ? getItemQueueRest().get(0) : getItemQueueTop().peekFirst();
    }

    public synchronized int getCurrentQueueSize() {
        return getItemQueueTop().size() + getItemQueueRest().size();
    }

    public Integer changeToNextQueue() {
        currentQueuePriority = queuesTop.ceilingKey(currentQueuePriority + 1);
        return currentQueuePriority;
    }

    public Integer getCurrentQueuePriority() {
        return currentQueuePriority;
    }

    private ArrayList<IItem> getItemQueueRest() {
        return queuesRest.get(currentQueuePriority);
    }

    private LinkedList<IItem> getItemQueueTop() {
        return queuesTop.get(currentQueuePriority);
    }
}
