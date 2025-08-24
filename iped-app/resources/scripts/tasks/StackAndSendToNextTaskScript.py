import threading

# Number of images or video frames to be processed at the same time
batchSize = 64

# Dictionary to accumulate batches before processing
THREAD_BATCHES = {}
# Dictionary to queue items that have been processed and are ready to be sent
PROCESSED_ITEMS = {}
# Unified lock to protect access to both dictionaries
BATCH_LOCK = threading.Lock()


def isImage(item):
    return item.getMediaType() is not None and item.getMediaType().toString().startswith('image')


def supported(item):
    supported = (
        item.getLength() is not None and
        item.getLength() > 0 and
        isImage(item) and
        item.getExtraAttribute('hasThumb') and
        item.getHash() is not None
    )
    return supported


'''
Main class
'''


class StackAndSendToNextTaskScript:

    def __init__(self):
        return

    def isEnabled(self):
        return True

    def getConfigurables(self):
        return []

    def processQueueEnd(self):
        return True

    def init(self, configuration):
        return

    def _apply_processing_to_batch(self, item_list):
        # Applies the processing logic
        for item in item_list:
            item.setExtraAttribute(str("csam_score"), str("50"))

    def process(self, item):

        worker_key = self.worker.id
        
        if not item.isQueueEnd() and not supported(item):
            return

        batch_to_process = None

        with BATCH_LOCK:
            if worker_key not in THREAD_BATCHES:
                THREAD_BATCHES[worker_key] = {'items': [], 'item_ids': set()}

            my_batch_data = THREAD_BATCHES[worker_key]

            if not item.isQueueEnd():
                my_batch_data['items'].append(item)
                my_batch_data['item_ids'].add(item.getId())

            size = len(my_batch_data['items'])
            is_batch_ready_to_process = size >= batchSize or (size > 0 and item.isQueueEnd())

            if is_batch_ready_to_process:
                # Once the batch is ready, it is removed from the accumulation phase
                # and sent for processing.
                batch_to_process = THREAD_BATCHES.pop(worker_key)

        if batch_to_process:
            # Processing occurs outside the lock and passes the worker_id
            logger.debug(f"Worker {worker_key}: Processing batch of {len(batch_to_process['items'])} items.")
            self._apply_processing_to_batch(batch_to_process['items'])

            # 2. After processing, the batch is added to the send queue.
            # This operation needs to be protected by a lock.
            with BATCH_LOCK:
                if worker_key not in PROCESSED_ITEMS:
                    PROCESSED_ITEMS[worker_key] = []
                PROCESSED_ITEMS[worker_key].extend(batch_to_process['items'])

    def sendToNextTask(self, item, oPythonTask):

        worker_key = self.worker.id

        items_to_send = None

        with BATCH_LOCK:
            # Removes the items to be sent from the global list with a lock
            # and only then sends them, to avoid blocking threads
            if worker_key in PROCESSED_ITEMS and len(PROCESSED_ITEMS[worker_key]) > 0:
                items_to_send = PROCESSED_ITEMS.pop(worker_key)

        if items_to_send:
            # Sends a batch of already processed items to the next task.
            isItemOnBatch = False
            for item_send in items_to_send:
                oPythonTask.sendToNextTaskSuper(item_send)
                if (item_send.getId() == item.getId()):
                    isItemOnBatch = True

            # If the current item that triggered the call was in the batch we just sent,
            # the work is done for this item, so we can exit.
            if isItemOnBatch:
                return

        with BATCH_LOCK:
            # The current item is in the list of pending items and will therefore be held back
            if worker_key in THREAD_BATCHES and item.getId() in THREAD_BATCHES[worker_key]['item_ids']:
                return

        # If it is not in the list of pending items and not in the list of
        # recently sent items, it is sent to the next task
        oPythonTask.sendToNextTaskSuper(item)

    def finish(self):
        return True
