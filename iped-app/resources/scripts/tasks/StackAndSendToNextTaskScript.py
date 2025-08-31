import traceback
import io
import os
import time
import sys
from java.lang import System
from java.lang import Thread
import threading

enabled = True
# Number of images or video frames to be processed at the same time
batchSize = 64

  
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
        self.itemList = []
        self.BATCH_LOCK = threading.Lock()

    def isEnabled(self):
        return True
        
    # Returns an optional list of configurable objects that can load/save parameters from/to config files. 
    def getConfigurables(self):
        return []        

    def processQueueEnd(self):
        return True
           
    def init(self, configuration):
        return
        
    def process(self, item):

        if not item.isQueueEnd() and not supported(item):
            return

        # Uses a local lock to change local item list to avoid concurrency issues
        with self.BATCH_LOCK:
            if(not item.isQueueEnd()):
                self.itemList.append(item)         
            
            if self.isToProcessBatch(item):
                for i in range(len(self.itemList)):
                    self.itemList[i].setExtraAttribute(str("csam_score"), str("50"))


    def sendToNextTask(self, item):
        
        isItemOnList = False
        
        # Uses a local lock to change local item list to avoid concurrency issues
        with self.BATCH_LOCK:
            # Checks if the item is in the list to be processed (e.g., not an image or queueend)
            if item in self.itemList:
                isItemOnList = True
        
            # Now we check if we just processed a batch, to clear the list and send everything to the next task
            if self.isToProcessBatch(item):                 
                for i in self.itemList:
                    self.javaTask.sendToNextTaskSuper(i)
                self.itemList.clear()
            
        # If the item is not on the list, send it to the next task.
        if(not isItemOnList):
            self.javaTask.sendToNextTaskSuper(item) 


    def isToProcessBatch(self, item):
        size = len(self.itemList)
        return size >= batchSize or (size > 0 and item.isQueueEnd())
        
    def finish(self):
        return True 