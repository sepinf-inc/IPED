#Looks for clues about crypto hardwares wallets.
#Searches the system registry and the setupapi.dev.[0-9_]log file.
#The ID's for the wallets come from Interpol (https://github.com/INTERPOL-Innovation-Centre/HardwareWallets_DF_List).
# On Linux, you need to install jep (pip install jep) and include jep.so in LD_LIBRARY_PATH.
# see https://github.com/sepinf-inc/IPED/wiki/User-Manual#python-modules
import json
import os
import re

configFile = 'hardwarewallets.json'

# The main class name must be equal to the script file name without .py extension
# One instance of this class is created by each processing thread and each thread calls the implemented methods of its own object.
class SearchHardwareWallets:

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
        #print("init")
        return

    
    def finish(self):

        query = "Hardware-Wallet:true"
        
        #set query into searcher
        searcher.setQuery(query)
        
        #search in case and return item ids
        ids = searcher.search().getIds()
        
        #create new bookmark and get its id
        bookmarkId = ipedCase.getBookmarks().newBookmark("Hardware Wallet")
        
        #set bookmark comment
        ipedCase.getBookmarks().setBookmarkComment(bookmarkId, "possible use of Hardware Krypto Wallets")
        
        #add item ids to created bookmark
        ipedCase.getBookmarks().addBookmark(ids, bookmarkId)
        
        #save changes synchronously
        ipedCase.getBookmarks().saveState(True)


    def process(self, item):
        from java.lang import System
        ipedRoot = System.getProperty('iped.root')
        f = open(os.path.join(ipedRoot, 'conf', configFile))
        wallets = json.load(f)
        SubItemID = 0
        # search for setupapi.dev.log or setupapi.dev.YYYYMMDD_hhmmss.log
        setupapi_regex = regex = r'(?i)setupapi\.dev\.[0-9_\.]*log'
        if item.getName() == 'SYSTEM-Report':
            ParsedText = item.getParsedTextCache().split('\n')
            # search for wallet in RegistryReport
            for w in wallets:
                regex = r'(?i).*' + w.get('VendorID') + '.*' + w.get('ProductID') + '.*'
                indices = [i for i, x in enumerate(ParsedText) if re.match(regex, x)]
                # read info for Device (until empty line)
                for i in indices:
                    hwInfo = 'Found HardWare-Wallet %s, %s\n' % (w.get('VendorName', ''), w.get('DeviceName', ''))
                    lineNo = i
                    while len(ParsedText[lineNo]) > 1:
                        hwInfo += ParsedText[lineNo] + '\n'
                        lineNo += 1
                    newSubItem(self, item, hwInfo, SubItemID)
        elif re.match(setupapi_regex, item.getName()):
            ''' setupapi.dev.log examples
            >>>  [Setup online Device Install (Hardware initiated) - USB\VID_0C45&PID_64AD\6&2e9d4003&0&4]
            >>>  Section start 2014/06/26 15:43:49.248
            '''
            ParsedText = item.getParsedTextCache().split('\n')
            for w in wallets:
                regex = r'(?i).*Device Install.*VID_' + w.get('VendorID') + '&PID_' + w.get('ProductID') + '.*'
                indices = [i for i, x in enumerate(ParsedText) if re.match(regex, x)]
                for i in indices:
                    # read two lines, second line contains timestamp of first seen
                    hwInfo = 'Found HardWare-Wallet %s, %s\n' % (w.get('VendorName', ''), w.get('DeviceName', ''))
                    hwInfo += ParsedText[i] + '\n'
                    hwInfo += ParsedText[i+1]
                    newSubItem(self, item, hwInfo, SubItemID)



def newSubItem(self, item, text, SubItemID):
    from iped.engine.data import Item

    newItem = Item()
    newItem.setParent(item)
    newItem.setName('Hardware-Wallet')
    #Should put information about found wallet in text field, but it doesn't, why?
    newItem.setParsedTextCache(text)
    newItem.setPath(item.getPath() + ">>" + newItem.getName())
    newItem.setExtraAttribute("Wallet-Info", text)
    newItem.setExtraAttribute("Hardware-Wallet", 'true')
    newItem.setSubItem(True)
    newItem.setSubitemId(SubItemID)
    from iped.engine.core import Statistics
    Statistics.get().incSubitemsDiscovered();
    newItem.setSumVolume(False);
    worker.processNewItem(newItem);
    
