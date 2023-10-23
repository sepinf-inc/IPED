#Looks for clues about crypto hardwares wallets.
#Searches the system registry and the setupapi.dev.[0-9_]log file.
#The ID's for the wallets come from Interpol (https://github.com/INTERPOL-Innovation-Centre/HardwareWallets_DF_List).
# On Linux, you need to install jep (pip install jep) and include jep.so in LD_LIBRARY_PATH.
# see https://github.com/sepinf-inc/IPED/wiki/User-Manual#python-modules
import json
import os
import re

from iped.parsers.registry import RegRipperParser
reportSuffix = RegRipperParser.FULL_REPORT_SUFFIX

configFile = 'hardwarewallets.json'
hwFound = 'Hardware-Wallet-Found'

bookmarkCreated = False


# The main class name must be equal to the script file name without .py extension
# One instance of this class is created by each processing thread and each thread calls the implemented methods of its own object.
class SearchHardwareWallets:
    
    wallets = None

    # Returns if this task is enabled or not. This could access options read by init() method.
    def isEnabled(self):
        return True

    # Returns an optional list of configurable objects that can load/save parameters from/to config files. 
    def getConfigurables(self):
        return []

    # Do some task initialization, like reading options, custom config files or model.
    # It is executed when application starts by each processing thread on its class instance.
    # @Params
    # configuration:    configuration manager by which configurables can be retrieved after populated.
    def init(self, configuration):
        if SearchHardwareWallets.wallets is not None:
            return
        from java.lang import System
        ipedRoot = System.getProperty('iped.root')
        with open(os.path.join(ipedRoot, 'conf', configFile)) as f:
            SearchHardwareWallets.wallets = json.load(f)
        return

    
    def finish(self):
        
        global bookmarkCreated
        if bookmarkCreated:
            return

        query = hwFound + ":true"
        
        #set query into searcher
        searcher.setQuery(query)
        
        #search in case and return item ids
        ids = searcher.search().getIds()
        
        if len(ids) == 0:
            return
        
        #create new bookmark and get its id
        bookmarkId = ipedCase.getBookmarks().newBookmark("Possible Hardware Wallets")
        
        #set bookmark comment
        ipedCase.getBookmarks().setBookmarkComment(bookmarkId, "Possible use of Hardware Crypto Wallets")
        
        #add item ids to created bookmark
        ipedCase.getBookmarks().addBookmark(ids, bookmarkId)
        
        #save changes synchronously
        ipedCase.getBookmarks().saveState(True)

        bookmarkCreated = True


    def process(self, item):
        subItemID = 0
        # search for setupapi.dev.log or setupapi.dev.YYYYMMDD_hhmmss.log
        setupapi_regex = regex = r'(?i)setupapi\.dev\.[0-9_\.]*log'
        if item.getName() == 'SYSTEM' + reportSuffix:
            ParsedText = item.getParsedTextCache().split('\n')
            # search for wallet in RegistryReport
            for w in SearchHardwareWallets.wallets:
                regex = r'(?i).*' + str(w.get('VendorID')) + '.*' + str(w.get('ProductID')) + '.*'
                indices = [i for i, x in enumerate(ParsedText) if re.match(regex, x)]
                # read info for Device (until empty line)
                for i in indices:
                    hwInfo = ''
                    lineNo = i
                    while len(ParsedText[lineNo]) > 1:
                        hwInfo += ParsedText[lineNo] + '\n'
                        lineNo += 1
                    newSubItem(self, item, hwInfo, subItemID, w)
                    subItemID += 1
        elif re.match(setupapi_regex, item.getName()):
            ''' setupapi.dev.log examples
            >>>  [Setup online Device Install (Hardware initiated) - USB\VID_0C45&PID_64AD\6&2e9d4003&0&4]
            >>>  Section start 2014/06/26 15:43:49.248
            '''
            ParsedText = item.getParsedTextCache().split('\n')
            for w in SearchHardwareWallets.wallets:
                regex = r'(?i).*Device Install.*VID_' + str(w.get('VendorID')) + '&PID_' + str(w.get('ProductID')) + '.*'
                indices = [i for i, x in enumerate(ParsedText) if re.match(regex, x)]
                for i in indices:
                    # read two lines, second line contains timestamp of first seen
                    hwInfo = ParsedText[i] + '\n'
                    hwInfo += ParsedText[i+1]
                    newSubItem(self, item, hwInfo, subItemID, w)
                    subItemID += 1



def newSubItem(self, item, text, subItemID, info):
    from iped.engine.data import Item

    newItem = Item()
    newItem.setParent(item)
    newItem.setName('Hardware-Wallet_' + str(subItemID))
    newItem.setPath(item.getPath() + ">>" + newItem.getName())
    newItem.getMetadata().set(hwFound, 'true')
    newItem.getMetadata().set('Hardware-Wallet-VendorID', str(info.get('VendorID')))
    newItem.getMetadata().set('Hardware-Wallet-ProductID', str(info.get('ProductID')))
    newItem.getMetadata().set('Hardware-Wallet-VendorName', str(info.get('VendorName')))
    newItem.getMetadata().set('Hardware-Wallet-DeviceName', str(info.get('DeviceName')))
    newItem.setSubItem(True)
    newItem.setSubitemId(subItemID)
    newItem.setSumVolume(False);
    
    # export item content to case storage
    from iped.engine.task import ExportFileTask
    from org.apache.commons.lang3 import StringUtils
    from java.io import ByteArrayInputStream
    exporter = ExportFileTask();
    exporter.setWorker(worker);
    bytes = StringUtils.getBytes(text, 'UTF-8')
    dataStream = ByteArrayInputStream(bytes);
    exporter.extractFile(dataStream, newItem, item.getLength());
    
    from iped.engine.core import Statistics
    Statistics.get().incSubitemsDiscovered();

    worker.processNewItem(newItem);
    
