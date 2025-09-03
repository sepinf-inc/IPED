from java.lang import System
import threading
from collections import deque

enabled = True
# Number of images or video frames to be processed at the same time
batchSize = 64

def isImage(item):
    """Checks if the item is an image."""
    return item.getMediaType() is not None and item.getMediaType().toString().startswith('image')
       
def supported(item):
    """Checks if the item has the necessary attributes for batch processing."""
    return (
        item.getLength() is not None and
        item.getLength() > 0 and
        isImage(item) and
        item.getExtraAttribute('hasThumb') and
        item.getHash() is not None
    )

'''
Main class corrected to be thread-safe, with improved separation of concerns
and robust error handling to prevent item loss.
'''
class StackAndSendToNextTaskScript:
    
    def __init__(self):
        # List to accumulate items until a batch is formed
        self.itemList = []
        # Queue for ALL items (single or batched) ready to be sent
        self.itemsToSend = deque()
        self.BATCH_LOCK = threading.Lock()

    def isEnabled(self):
        return True
        
    def getConfigurables(self):
        return []        

    def processQueueEnd(self):
        return True
           
    def init(self, configuration):
        return

    def _process_batch(self, batch_items):
        """
        Processes each item in a given batch by setting the required attributes.
        """
        for item in batch_items:
            try:
                item.setExtraAttribute(str("csam_score"), str("50"))
            except Exception as e:
                logger.error("Failed to process item attribute: " + str(e))
        
    def process(self, item):
        """
        Acts as a dispatcher: decides if an item should be batched or
        sent directly. Includes error handling to prevent item loss.
        """
        try:
            with self.BATCH_LOCK:
                # Step 1: Classify the incoming item.
                # If supported, add to the batch list. Otherwise, add directly to the send queue.
                if supported(item):
                    self.itemList.append(item)
                else:
                    self.itemsToSend.append(item)

                # Step 2: Check if the batch needs to be flushed.
                # This happens if the batch is full, or if the end of the queue is signaled.
                if (len(self.itemList) >= batchSize or item.isQueueEnd()) and self.itemList:
                    # The batch is ready, process it before sending.
                    self._process_batch(self.itemList)
                    
                    # Move the now-processed items to the sending queue.
                    self.itemsToSend.extend(self.itemList)
                    self.itemList.clear()
                    
        except Exception as e:
            # SAFETY NET: If any unexpected error occurs, log it and queue the
            # original item to be sent to the next task, ensuring it is not lost.
            logger.error("An unexpected error occurred in process(). Queuing item to be sent as-is. Error: " + str(e))
            with self.BATCH_LOCK:
                self.itemsToSend.append(item)
                
            # Re-throw the exception to notify the calling framework of the failure.
            raise

    def sendToNextTask(self, item):
        """
        Acts as a simple sender. It drains the itemsToSend queue and sends
        everything that the process() method has prepared.
        """
        items_to_send_now = deque()

        with self.BATCH_LOCK:
            # Drains the output queue to a local variable for sending.
            while self.itemsToSend:
                items_to_send_now.append(self.itemsToSend.popleft())

        # Sends all items that were ready.
        # This is done outside the lock.
        while items_to_send_now:
            self.javaTask.sendToNextTaskSuper(items_to_send_now.popleft())
        
    def finish(self):
        """
        This method is not used to flush remaining items.
        The end of the item stream is signaled by an isQueueEnd() item.
        """
        return True